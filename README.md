# Pine3 View

Painel pessoal para o Home Assistant, inspirado no Apple Home. Organiza a casa
automaticamente por divisão (Áreas → Dispositivos → Entidades), sem YAML.
Código original, feito de raiz.

## Instalar
1. Copiar `custom_components/pine3_view` para `/config/custom_components/`.
2. Reiniciar o Home Assistant.
3. Definições → Dispositivos e serviços → Adicionar integração → **Pine3 View**.
4. Abrir **Pine3 View** na barra lateral.

## Como se constrói sozinho
O servidor (`discovery.py`) lê os registos do Home Assistant e monta a casa:
- **Áreas → divisões**, ordenadas por piso e nome; ícone escolhido pelo nome (Cozinha, Quarto, Casa de banho…).
- **Dispositivos → mosaicos**: cada dispositivo dá um mosaico, com a entidade principal (clima > multimédia > estores > luz > tomada…). Os sensores do mesmo dispositivo (potência, bateria, humidade) aparecem como extras no mosaico.
- Relés de vários canais dão um mosaico por canal. O nome da divisão é retirado do nome do dispositivo ("Sala Candeeiro" → "Candeeiro").
- Entidades escondidas, desativadas, de diagnóstico ou configuração são ignoradas.
- Sensores de temperatura e humidade alimentam o resumo de cada divisão.
- Quando adicionas ou mudas dispositivos/áreas no HA, o painel atualiza-se sozinho.
- Podes esconder divisões e dispositivos no próprio painel (guardado no HA, não no browser).

## Visual
Fundo ameixa (e mais 5), cartões creme quando ligados, botão de energia por divisão e por dispositivo,
pontos de estado, cartões de Clima, Segurança e "A tocar", dock em pílula.
Tipografia: [Outfit](https://github.com/Outfitio/Outfit-Fonts) e [Figtree](https://github.com/erikdkennedy/figtree), ambas SIL OFL 1.1 (licenças em `frontend/fonts/`).

## Testes
```
pip install -r requirements_test.txt
pytest
```

## Roadmap
Modo de edição (arrastar, mudar tamanho), ecrã de descanso, layouts alternativos.
