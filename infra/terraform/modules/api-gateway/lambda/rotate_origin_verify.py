"""Rotacao do header X-Origin-Verify no protocolo do Secrets Manager.

Etapas: createSecret, setSecret, testSecret, finishSecret.
O valor novo vira current; o current anterior permanece em previous e
continua aceito pela regra do WAF ate a rotacao seguinte. O CloudFront
passa a enviar o valor novo. Enquanto a distribuicao propaga, o valor
anterior ainda passa no WAF regional.
"""

import json
import logging
import os
import secrets as token_secrets

import boto3
from botocore.exceptions import ClientError

logger = logging.getLogger()
logger.setLevel(logging.INFO)

HEADER_NAME = os.environ.get("HEADER_NAME", "X-Origin-Verify")
ORIGIN_ID = os.environ.get("ORIGIN_ID", "api-gateway")
DISTRIBUTION_TAG_NAME = os.environ["DISTRIBUTION_TAG_NAME"]
WEB_ACL_NAME = os.environ["WEB_ACL_NAME"]
WEB_ACL_ID = os.environ["WEB_ACL_ID"]
WEB_ACL_SCOPE = os.environ.get("WEB_ACL_SCOPE", "REGIONAL")

secrets_manager = boto3.client("secretsmanager")
cloudfront = boto3.client("cloudfront")
waf = boto3.client("wafv2")


def handler(event, context):
    arn = event["SecretId"]
    token = event["ClientRequestToken"]
    step = event["Step"]
    logger.info("rotacao step=%s", step)

    if step == "createSecret":
        create_secret(arn, token)
    elif step == "setSecret":
        set_secret(arn, token)
    elif step == "testSecret":
        test_secret(arn, token)
    elif step == "finishSecret":
        finish_secret(arn, token)
    else:
        raise ValueError(f"etapa desconhecida: {step}")

    return {"status": "ok", "step": step}


def create_secret(arn, token):
    try:
        secrets_manager.get_secret_value(SecretId=arn, VersionId=token, VersionStage="AWSPENDING")
        logger.info("AWSPENDING ja existe para este token")
        return
    except ClientError as error:
        if error.response["Error"]["Code"] != "ResourceNotFoundException":
            raise

    current = _load(arn, "AWSCURRENT")
    pending = {
        "current": _new_token(),
        "previous": current["current"],
    }
    secrets_manager.put_secret_value(
        SecretId=arn,
        ClientRequestToken=token,
        SecretString=json.dumps(pending),
        VersionStages=["AWSPENDING"],
    )


def set_secret(arn, token):
    pending = _load(arn, "AWSPENDING", token)
    _update_waf(pending["current"], pending["previous"])
    _update_cloudfront(pending["current"])


def test_secret(arn, token):
    pending = _load(arn, "AWSPENDING", token)
    header = _cloudfront_header()
    if header != pending["current"]:
        raise RuntimeError("CloudFront ainda nao tem o valor pendente no origin api-gateway")
    accepted = _waf_header_values()
    if pending["current"] not in accepted or pending["previous"] not in accepted:
        raise RuntimeError("WAF nao aceita o par current/previous pendente")


def finish_secret(arn, token):
    metadata = secrets_manager.describe_secret(SecretId=arn)
    versions = metadata["VersionIdsToStages"]
    if token not in versions:
        raise RuntimeError("token de rotacao ausente no segredo")
    if "AWSCURRENT" in versions[token]:
        return

    current_version = None
    for version_id, stages in versions.items():
        if "AWSCURRENT" in stages:
            current_version = version_id
            break
    if current_version is None:
        raise RuntimeError("AWSCURRENT nao encontrado")

    secrets_manager.update_secret_version_stage(
        SecretId=arn,
        VersionStage="AWSCURRENT",
        MoveToVersionId=token,
        RemoveFromVersionId=current_version,
    )


def _load(arn, stage, version_id=None):
    kwargs = {"SecretId": arn, "VersionStage": stage}
    if version_id is not None:
        kwargs["VersionId"] = version_id
    response = secrets_manager.get_secret_value(**kwargs)
    payload = json.loads(response["SecretString"])
    if "current" not in payload or "previous" not in payload:
        raise RuntimeError("segredo sem current/previous")
    return payload


def _new_token():
    return token_secrets.token_urlsafe(36)


def _update_waf(current, previous):
    acl = waf.get_web_acl(Name=WEB_ACL_NAME, Scope=WEB_ACL_SCOPE, Id=WEB_ACL_ID)
    web_acl = acl["WebACL"]
    matches = [
        statement
        for statement in _byte_matches(web_acl["Rules"])
        if _is_origin_header(statement)
    ]
    if len(matches) != 2:
        raise RuntimeError(f"regra X-Origin-Verify com {len(matches)} byte matches; esperados 2")
    matches[0]["SearchString"] = current
    matches[1]["SearchString"] = previous
    waf.update_web_acl(
        Name=WEB_ACL_NAME,
        Scope=WEB_ACL_SCOPE,
        Id=WEB_ACL_ID,
        LockToken=acl["LockToken"],
        DefaultAction=web_acl["DefaultAction"],
        Rules=web_acl["Rules"],
        VisibilityConfig=web_acl["VisibilityConfig"],
        Description=web_acl.get("Description", ""),
        CustomResponseBodies=web_acl.get("CustomResponseBodies") or {},
    )


def _byte_matches(node):
    found = []
    if isinstance(node, dict):
        statement = node.get("ByteMatchStatement")
        if isinstance(statement, dict):
            found.append(statement)
        for value in node.values():
            found.extend(_byte_matches(value))
    elif isinstance(node, list):
        for item in node:
            found.extend(_byte_matches(item))
    return found


def _is_origin_header(statement):
    field = statement.get("FieldToMatch") or {}
    header = field.get("SingleHeader") or {}
    name = header.get("Name", "")
    return name.lower() == HEADER_NAME.lower()


def _waf_header_values():
    acl = waf.get_web_acl(Name=WEB_ACL_NAME, Scope=WEB_ACL_SCOPE, Id=WEB_ACL_ID)
    values = []
    for statement in _byte_matches(acl["WebACL"]["Rules"]):
        if _is_origin_header(statement):
            raw = statement.get("SearchString", "")
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8")
            values.append(raw)
    return values


def _distribution_id():
    marker = None
    matches = []
    while True:
        kwargs = {"Marker": marker} if marker else {}
        page = cloudfront.list_distributions(**kwargs)
        listing = page.get("DistributionList") or {}
        for item in listing.get("Items") or []:
            tags = cloudfront.list_tags_for_resource(Resource=item["ARN"])
            names = {
                tag["Key"]: tag["Value"]
                for tag in tags.get("Tags", {}).get("Items", [])
            }
            if names.get("Name") == DISTRIBUTION_TAG_NAME:
                matches.append(item["Id"])
        if not listing.get("IsTruncated"):
            break
        marker = listing.get("NextMarker")
    if len(matches) != 1:
        raise RuntimeError(
            f"esperada 1 distribuicao com Name={DISTRIBUTION_TAG_NAME}, achei {len(matches)}"
        )
    return matches[0]


def _origin(config):
    for origin in config["Origins"]["Items"]:
        if origin["Id"] == ORIGIN_ID:
            return origin
    raise RuntimeError(f"origin {ORIGIN_ID} ausente na distribuicao")


def _cloudfront_header():
    config = cloudfront.get_distribution_config(Id=_distribution_id())
    return _header_value(_origin(config["DistributionConfig"]))


def _header_value(origin):
    headers = (origin.get("CustomHeaders") or {}).get("Items") or []
    for header in headers:
        if header["HeaderName"].lower() == HEADER_NAME.lower():
            return header["HeaderValue"]
    return None


def _update_cloudfront(value):
    distribution_id = _distribution_id()
    response = cloudfront.get_distribution_config(Id=distribution_id)
    config = response["DistributionConfig"]
    origin = _origin(config)
    if _header_value(origin) == value:
        logger.info("CloudFront ja envia o valor pendente")
        return
    headers = origin.setdefault("CustomHeaders", {"Quantity": 0, "Items": []})
    items = headers.get("Items") or []
    replaced = False
    for header in items:
        if header["HeaderName"].lower() == HEADER_NAME.lower():
            header["HeaderValue"] = value
            replaced = True
    if not replaced:
        items.append({"HeaderName": HEADER_NAME, "HeaderValue": value})
    headers["Items"] = items
    headers["Quantity"] = len(items)
    cloudfront.update_distribution(
        Id=distribution_id,
        IfMatch=response["ETag"],
        DistributionConfig=config,
    )
