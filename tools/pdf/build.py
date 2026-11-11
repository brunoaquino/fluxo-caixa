#!/usr/bin/env python3
"""Gera os PDFs dos entregáveis a partir dos Markdown do repositório.

Pipeline: Markdown (GFM) -> HTML com pandoc -> PDF com Chrome headless.
Uso: python3 tools/pdf/build.py [--saida DIR] [--so NOME.pdf ...]
"""

from __future__ import annotations

import argparse
import html
import os
import re
import shutil
import subprocess
import sys
import tempfile
from datetime import date
from pathlib import Path

from entregaveis import COMPLETO, ENTREGAVEIS

try:
    import pymupdf
except ImportError:
    pymupdf = None

RAIZ = Path(__file__).resolve().parents[2]
AQUI = Path(__file__).resolve().parent
REPO_URL = "https://github.com/brunoaquino/fluxo-caixa"
AUTOR = "Bruno Aquino"
PROJETO = "Fluxo de Caixa"
DESAFIO = "Desafio de Arquiteto de Soluções"
PASTA_SAIDA = "documentos-entregaveis-desafio"

CHROME_CANDIDATOS = [
    os.environ.get("CHROME_BIN", ""),
    "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
    "/Applications/Chromium.app/Contents/MacOS/Chromium",
    shutil.which("google-chrome") or "",
    shutil.which("chromium") or "",
    shutil.which("chromium-browser") or "",
]

HREF_RE = re.compile(r'(href)="([^"]*)"')
SRC_RE = re.compile(r'(src)="([^"]*)"')
ID_RE = re.compile(r'\bid="([^"]+)"')
H_RE = re.compile(r"<h([1-3])([^>]*)>(.*?)</h\1>", re.S)
TAG_RE = re.compile(r"<[^>]+>")


def slug_de(caminho: str) -> str:
    return "d-" + re.sub(r"[^a-z0-9]+", "-", caminho.lower()).strip("-")


def chrome() -> str:
    for c in CHROME_CANDIDATOS:
        if c and Path(c).exists():
            return c
    sys.exit("Chrome/Chromium não encontrado. Defina CHROME_BIN.")


def pandoc_html(md: Path) -> str:
    return subprocess.run(
        ["pandoc", "-f", "gfm", "-t", "html5", "--wrap=none", "--highlight-style=pygments", str(md)],
        check=True, capture_output=True, text=True,
    ).stdout


def css_destaque() -> str:
    with tempfile.TemporaryDirectory() as tmp:
        tpl = Path(tmp) / "t.html"
        tpl.write_text("$highlighting-css$", encoding="utf-8")
        return subprocess.run(
            ["pandoc", "-f", "gfm", "-t", "html5", "--highlight-style=pygments", f"--template={tpl}"],
            input="```java\nclass A {}\n```\n", check=True, capture_output=True, text=True,
        ).stdout


class Montador:
    """Converte um conjunto de Markdown em um único HTML com links reescritos."""

    def __init__(self, pdf_de: dict[str, str], pdf_atual: str, fontes_no_pdf: set[str]):
        self.pdf_de = pdf_de
        self.pdf_atual = pdf_atual
        self.fontes_no_pdf = fontes_no_pdf

    def _alvo_link(self, origem: str, href: str) -> str:
        if href.startswith(("http://", "https://", "mailto:")):
            return href
        slug_origem = slug_de(origem)
        if href.startswith("#"):
            return f"#{slug_origem}--{href[1:]}"
        caminho, _, ancora = href.partition("#")
        alvo = os.path.normpath(os.path.join(os.path.dirname(origem), caminho))
        if alvo in self.fontes_no_pdf:
            return f"#{slug_de(alvo)}--{ancora}" if ancora else f"#{slug_de(alvo)}"
        if alvo in self.pdf_de:
            return f"{REPO_URL}/blob/main/{PASTA_SAIDA}/{self.pdf_de[alvo]}"
        tipo = "tree" if (RAIZ / alvo).is_dir() else "blob"
        return f"{REPO_URL}/{tipo}/main/{alvo}" + (f"#{ancora}" if ancora else "")

    def fragmento(self, origem: str) -> str:
        corpo = pandoc_html(RAIZ / origem)
        slug = slug_de(origem)
        corpo = ID_RE.sub(lambda m: f'id="{slug}--{m.group(1)}"', corpo)
        corpo = HREF_RE.sub(lambda m: f'href="{html.escape(self._alvo_link(origem, html.unescape(m.group(2))))}"', corpo)

        def src(m: re.Match) -> str:
            v = m.group(2)
            if v.startswith(("http://", "https://", "data:", "file:")):
                return m.group(0)
            return f'src="{(RAIZ / os.path.dirname(origem) / v).resolve().as_uri()}"'

        corpo = SRC_RE.sub(src, corpo)
        rotulo = html.escape(origem)
        return (
            f'<section class="doc" id="{slug}">'
            f'<div class="origem">Fonte: <a href="{REPO_URL}/blob/main/{origem}">{rotulo}</a></div>'
            f"{corpo}</section>"
        )


def titulos(fragmento: str, niveis: tuple[int, ...]) -> list[tuple[int, str, str]]:
    saida = []
    for m in H_RE.finditer(fragmento):
        nivel = int(m.group(1))
        if nivel not in niveis:
            continue
        ident = ID_RE.search(m.group(2))
        texto = html.unescape(TAG_RE.sub("", m.group(3))).strip()
        if ident and texto:
            saida.append((nivel, ident.group(1), texto))
    return saida


def sumario_html(itens: list[tuple[int, str, str]]) -> str:
    linhas = [
        f'<li class="n{n}"><a href="#{i}"><span class="t">{html.escape(t)}</span>'
        f'<span class="pg" data-t="{html.escape(t)}"></span></a></li>'
        for n, i, t in itens
    ]
    return '<nav class="sumario"><h2 class="sumario-titulo">Sumário</h2><ol>' + "".join(linhas) + "</ol></nav>"


def capa_html(titulo: str, subtitulo: str, numero: str) -> str:
    hoje = date.today().strftime("%d/%m/%Y")
    return f"""
<section class="capa">
  <div class="capa-faixa">
    <div class="capa-marca"><span class="capa-logo">FC</span><span>{PROJETO}</span></div>
    <div class="capa-numero">{html.escape(numero)}</div>
    <h1 class="capa-titulo">{html.escape(titulo)}</h1>
    <p class="capa-subtitulo">{html.escape(subtitulo)}</p>
  </div>
  <div class="capa-meta">
    <dl>
      <div><dt>Projeto</dt><dd>{PROJETO} — {DESAFIO}</dd></div>
      <div><dt>Autor</dt><dd>{AUTOR}</dd></div>
      <div><dt>Data</dt><dd>{hoje}</dd></div>
      <div><dt>Repositório</dt><dd><a href="{REPO_URL}">{REPO_URL.removeprefix('https://')}</a></dd></div>
    </dl>
    <p class="capa-stack">Java 25 · Spring Boot 4 · AWS ECS Fargate · Terraform · C4 · DDD · ADRs</p>
  </div>
</section>"""


def pagina_html(titulo_doc: str, capa: str, sumario: str, corpo: str, css_extra: str) -> str:
    css = (AQUI / "estilo.css").read_text(encoding="utf-8")
    cabecalho = f"{PROJETO} · {titulo_doc}".replace('"', "'")
    return f"""<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8"><title>{html.escape(titulo_doc)} — {PROJETO}</title>
<style>{css}
@page {{ @top-left {{ content: "{cabecalho}"; }} }}
{css_extra}</style></head>
<body>{capa}{sumario}<main>{corpo}</main></body></html>"""


def imprimir(html_path: Path, pdf_path: Path) -> None:
    subprocess.run(
        [
            chrome(), "--headless=new", "--disable-gpu", "--no-sandbox", "--hide-scrollbars",
            "--allow-file-access-from-files", "--no-pdf-header-footer", "--generate-pdf-document-outline",
            "--virtual-time-budget=15000", f"--print-to-pdf={pdf_path}", html_path.as_uri(),
        ],
        check=True, capture_output=True,
    )


def construir(item: dict, numero: str, pdf_de: dict[str, str], saida: Path, tmp: Path, css_hl: str) -> Path:
    fontes = item["fontes"]
    montador = Montador(pdf_de, item["pdf"], set(fontes))
    fragmentos = [montador.fragmento(f) for f in fontes]
    if item.get("sumario_nivel", 2) == 1:
        itens = [t for fr in fragmentos for t in titulos(fr, (1,))[:1]]
    else:
        itens = [t for fr in fragmentos for t in titulos(fr, (2,))]
    corpo = "".join(fragmentos)
    pagina = pagina_html(item["titulo"], capa_html(item["titulo"], item["subtitulo"], numero), sumario_html(itens), corpo, css_hl)
    return _gravar(item["pdf"], pagina, saida, tmp)


def construir_completo(pdf_de: dict[str, str], saida: Path, tmp: Path, css_hl: str) -> Path:
    todas = [f for e in ENTREGAVEIS for f in e["fontes"]]
    montador = Montador(pdf_de, COMPLETO["pdf"], set(todas))
    partes, itens = [], []
    for n, e in enumerate(ENTREGAVEIS):
        pid = f"parte-{n:02d}"
        itens.append((0, pid, f"{n:02d} · {e['titulo']}"))
        partes.append(
            f'<section class="parte" id="{pid}"><div class="parte-numero">Parte {n:02d}</div>'
            f'<h1 class="parte-titulo">{html.escape(e["titulo"])}</h1>'
            f'<p class="parte-subtitulo">{html.escape(e["subtitulo"])}</p></section>'
        )
        for f in e["fontes"]:
            fr = montador.fragmento(f)
            itens += titulos(fr, (1,))[:1]
            partes.append(fr)
    pagina = pagina_html(
        COMPLETO["titulo"], capa_html(COMPLETO["titulo"], COMPLETO["subtitulo"], "Volume único"),
        sumario_html(itens), "".join(partes), css_hl,
    )
    return _gravar(COMPLETO["pdf"], pagina, saida, tmp)


def _normalizar(texto: str) -> str:
    return re.sub(r"\s+", " ", texto).strip().casefold()


def _numerar_sumario(pagina: str, pdf_path: Path) -> str:
    """Preenche o número de página de cada entrada do sumário a partir do outline do PDF."""
    if pymupdf is None:
        return pagina
    with pymupdf.open(pdf_path) as doc:
        outline = [(_normalizar(t), p) for _, t, p in doc.get_toc(simple=True)]
    cursor = 0

    def trocar(m: re.Match) -> str:
        nonlocal cursor
        alvo = _normalizar(html.unescape(m.group(1)))
        for k in range(cursor, len(outline)):
            if outline[k][0] == alvo:
                cursor = k + 1
                return f'<span class="pg">{outline[k][1]}</span>'
        return '<span class="pg"></span>'

    return re.sub(r'<span class="pg" data-t="([^"]*)"></span>', trocar, pagina)


def _gravar(nome_pdf: str, pagina: str, saida: Path, tmp: Path) -> Path:
    html_path = tmp / (Path(nome_pdf).stem + ".html")
    pdf_path = saida / nome_pdf
    html_path.write_text(pagina, encoding="utf-8")
    imprimir(html_path, pdf_path)
    numerada = _numerar_sumario(pagina, pdf_path)
    if numerada != pagina:
        html_path.write_text(numerada, encoding="utf-8")
        imprimir(html_path, pdf_path)
    return pdf_path


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--saida", default=str(RAIZ / PASTA_SAIDA))
    ap.add_argument("--so", nargs="*", help="gera só estes PDFs")
    ap.add_argument("--manter-html", action="store_true")
    args = ap.parse_args()

    saida = Path(args.saida)
    saida.mkdir(parents=True, exist_ok=True)
    pdf_de = {f: e["pdf"] for e in ENTREGAVEIS for f in e["fontes"]}
    faltando = [f for f in pdf_de if not (RAIZ / f).exists()]
    if faltando:
        sys.exit(f"Fontes inexistentes: {faltando}")

    css_hl = css_destaque()
    tmp = Path(tempfile.mkdtemp(prefix="fc-pdf-"))
    for n, e in enumerate(ENTREGAVEIS):
        if args.so and e["pdf"] not in args.so:
            continue
        print("gerando", construir(e, f"Documento {n:02d}", pdf_de, saida, tmp, css_hl).relative_to(RAIZ))
    if not args.so or COMPLETO["pdf"] in args.so:
        print("gerando", construir_completo(pdf_de, saida, tmp, css_hl).relative_to(RAIZ))
    if args.manter_html:
        print("HTML intermediário em", tmp)
    else:
        shutil.rmtree(tmp)


if __name__ == "__main__":
    main()
