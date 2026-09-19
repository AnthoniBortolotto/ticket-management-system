# ADR 0002 — Docker Compose para desenvolver, Kubernetes para demonstrar

- **Status:** aceito
- **Data:** 2026-09-19

## Contexto

O projeto precisa de infraestrutura local para três coisas diferentes, e elas têm
exigências opostas:

1. **Desenvolver** — ciclo de edição rápido, hot reload, banco de pé.
2. **Rodar o E2E** — stack completa e reproduzível, backend e frontend de verdade.
3. **Demonstrar competência de deploy** — é um projeto de portfólio; alguém vai olhar.

A tentação é escolher uma ferramenta só. Rodar Kubernetes local (kind, minikube) para
tudo atende (2) e (3) e destrói (1): cada alteração vira build de imagem, carga no
cluster e rollout, e o que levava segundos passa a levar dezenas. Pior, config passa a
existir em dois lugares — Compose e manifests — e as duas cópias divergem em silêncio.

## Decisão

**Docker Compose é a infraestrutura de desenvolvimento e de E2E. Kubernetes existe no
repositório como demonstração de deploy, e não é usado no dia a dia.**

O Compose serve os dois fluxos sem profile nem flag extra:

```bash
docker compose -f docker/docker-compose.yml up -d            # stack completa (E2E)
docker compose -f docker/docker-compose.yml up -d postgres   # só o banco (hot reload)
```

Os manifests em `k8s/` sobem as **mesmas imagens** que o Compose constrói, carregadas no
kind com `kind load docker-image`. Não há um segundo caminho de build.

### Um processo por container

O `docker-compose.yml` tem três serviços, não um container com tudo dentro. Um container
só faria o Postgres reiniciar junto com a API e transformaria o supervisor em script de
shell — perdendo exatamente o que torna container útil: reiniciar, escalar e observar
cada peça em separado.

### O que mantém o `k8s/` honesto

Manifest que não roda apodrece: ele continua verde no `git diff` enquanto descreve um
sistema que não existe mais. Como aqui ele não é exercitado diariamente, a regra de
manutenção precisa ser explícita, e está no
[CLAUDE.md](../../CLAUDE.md#kubernetes-em-k8s):

> Variável de ambiente, porta, probe ou imagem que muda no `docker-compose.yml` muda no
> `k8s/base/` na mesma alteração.

## Consequências

- O loop de desenvolvimento continua rápido: o Compose sobe só o Postgres e as
  aplicações rodam na máquina.
- O `k8s/` é uma superfície a manter em dia sem retorno diário. É custo aceito
  conscientemente, em troca de o portfólio mostrar deploy em Kubernetes.
- Não há Helm chart, overlay de ambiente nem GitOps. O `k8s/base` é um kustomize único,
  suficiente para `kubectl apply -k` contra um kind. Se um dia existirem ambientes de
  verdade, aí sim entram overlays — e este ADR é substituído.
- O Ingress assume o controller nginx instalado no kind (`ingress-nginx`). O
  `kind-config.yaml` já marca o nó com `ingress-ready=true` e mapeia as portas 80 e 443.
