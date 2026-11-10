# Representação monetária

- **Status:** Aceito
- **Data:** 2026-11-10

## Contexto e problema

Saldo errado por centavo destrói a confiança no consolidado mais rápido do que uma indisponibilidade. O erro clássico é `double`: 0,10 não existe exatamente em binário. O valor atravessa JSON de request, coluna SQL, evento e cache.

## Drivers

- Duas casas decimais, meio para cima no sentido bancário usual de escala, sem surpresa de `0.30000000000000004`.
- Moeda única BRL nesta versão.
- Valor sempre positivo no lançamento; o sinal contábil é o tipo (crédito ou débito), não o número.
- Contrato do evento já fixado como string `"150.00"`.

## Opções consideradas

### 1. `double` / `float` de ponta a ponta

- Positivo: menos código.
- Negativo: inaceitável para dinheiro. Rejeitado sem meio-termo.

### 2. Inteiro de centavos (`15000` = R$ 150,00)

- Positivo: aritmética exata, fácil de somar no evento.
- Negativo: toda borda precisa lembrar a unidade. Um cliente que manda `150.00` pensando em reais grava R$ 1,50 ou R$ 15.000 se alguém converter duas vezes. O blueprint mostra o request em reais (`150.00`) e o evento em string decimal, não em centavos.

### 3. `BigDecimal` no domínio, `NUMERIC(19,2)` no Postgres, string de duas casas no evento

Value object `Dinheiro` com escala 2, `> 0` e moeda `BRL`. A API aceita número JSON no request, como no exemplo do blueprint (`"valor": 150.00`), e o desserializador usa `BigDecimal`, nunca `double`. A resposta devolve string de duas casas para o cliente não reintroduzir binário ao reexibir. O evento usa string, `toPlainString`, sempre com duas casas. A soma do saldo usa `BigDecimal` e a coluna `NUMERIC`.

- Positivo: o tipo carrega a unidade. O evento é estável entre linguagens (JavaScript que lesse número perderia precisão em valores grandes; string não). `NUMERIC(19,2)` cabe muito além do caixa de um comerciante e rejeita escala ilegal no banco.
- Negativo: o request é número e o evento é string. Essa assimetria está no blueprint e é uma aresta real: um cliente JavaScript que faça `JSON.parse` e reenvie o número pode ter alterado a escala antes de chegar aqui. O serviço normaliza para duas casas ao construir `Dinheiro`. Valor com três casas (`10.125`) é 400, não arredondamento silencioso — arredondar esconderia erro de integração.

## Decisão

Opção 3. Proibido `double` em campo de valor, inclusive em teste. Comparação de dinheiro por `compareTo` na mesma escala, não por igualdade de objeto com escala diferente (`10.0` versus `10.00` são o mesmo valor depois da normalização).

## Consequências

### Positivas

- A soma de muitos lançamentos de centavos fecha com o saldo do dia.
- O contrato do evento não depende de biblioteca decimal do consumidor: é texto.

### Negativas

- Três representações (número no POST, numeric no banco, string no evento e na resposta) pedem teste de contrato em cada borda. Sem esse teste, a decisão vira intenção.
- Multi-moeda não cabe neste value object sem trocar a invariante de “sempre BRL”. Quando vier, vem com ADR novo, não com um `if` de moeda espalhado.
