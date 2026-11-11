# GitOps com ArgoCD, como evolução

- **Status:** Aceito
- **Data:** 2026-11-10
- **Complementa:** [ADR 0020](0020-estrategia-de-deploy.md)

## Contexto e problema

O deploy de hoje é push: o GitHub Actions assume uma role e chama `UpdateService` no ECS, com a imagem que o CI já construiu. Funciona com dois serviços. Deixa de funcionar como modelo quando o que se implanta é um conjunto de manifestos (quando o EKS existir) ou quando o que está rodando diverge do que está no Git e ninguém vê.

## Drivers

- Build once, já decidido: a mesma imagem promove dev → hml → prod.
- O Git como fonte do que deveria estar rodando.
- Drift visível.
- Não operar ArgoCD antes de existir o cluster que ele gerencia.

## Opções consideradas

### 1. Push pelo Actions, para sempre

- Positivo: o pipeline atual. Um log, uma role, um `UpdateService`.
- Negativo: o estado desejado fica repartido entre o Terraform (a task definition) e o workflow (a tag da imagem). Os dois podem divergir, e o próprio comentário do workflow de CD já avisa: o próximo `terraform apply` devolve a tag antiga se ela não tiver sido alinhada. Isso é drift com nome.

### 2. ArgoCD agora, em ECS

- Positivo: um só modelo mental.
- Negativo: ArgoCD não é o controlador nativo de service ECS. Forçar GitOps em ECS antes do EKS cria um controlador terceiro para um recurso que o Terraform e o Actions já cobrem. Dois donos do service.

### 3. Push no ECS agora; ArgoCD quando houver EKS

Repositório de manifests (ou uma pasta por ambiente), ArgoCD em modo pull, app-of-apps, ApplicationSet por serviço. A imagem promovida é um commit de tag nesse repositório, não um `kubectl` na workstation. Drift detection ligado: o que mudou no cluster fora do Git aparece como diff, não como “ajuste”.

- Positivo: o cluster não aceita o que o Git não diz. O mesmo mecanismo serve aos add-ons da stack (Karpenter, External Secrets, cert-manager), senão a plataforma vira clique.
- Negativo: mais um componente de alta disponibilidade. ArgoCD fora do ar não tira o caixa do ar (o que já está implantado continua), mas trava o deploy. Precisa de dono.

## Decisão

Opção 3. Enquanto o scheduler for ECS, o deploy continua o do ADR 0020: rolling, circuit breaker, a tag imutável publicada pelo CI, promoção dev → hml → prod com aprovação no environment. O alinhamento da tag com o Terraform é dever do pipeline, não um lembrete no comentário do YAML.

Quando o ADR 0026 disparar, o GitOps entra no mesmo passo do cluster, não “na fase 2 do EKS”. Cluster sem ArgoCD e com apply manual é o modo mais rápido de a stack open source divergir do documento.

Pull, não push: o cluster busca o Git. A role de CI não precisa de privilégio de `kubectl apply` em prod. A role do ArgoCD precisa, e fica no cluster, com a trilha de quem aprovou o commit.

## Consequências

### Positivas

- A evolução tem repositório, direção do fluxo e o que fazer com drift, antes de a ferramenta existir.
- Build once não muda com a ferramenta. Muda o ato de implantar.

### Negativas

- Até lá, o drift Terraform versus tag do ECS é um risco real. O documento de [CI/CD](../14-cicd-e-qualidade.md) trata esse ponto no pipeline atual, para não esperar o ArgoCD resolver um problema que o ECS já tem.
