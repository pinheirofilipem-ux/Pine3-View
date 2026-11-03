# Pine3 View

Painel pessoal para o Home Assistant, inspirado no Apple Home. Organiza a casa
automaticamente por divisão (Áreas → Dispositivos → Entidades), sem YAML.
Código original, feito de raiz.

## Instalar
1. Copiar `custom_components/pine3_view` para `/config/custom_components/`.
2. Reiniciar o Home Assistant.
3. Definições → Dispositivos e serviços → Adicionar integração → **Pine3 View**.
4. Abrir **Pine3 View** na barra lateral.

## Estado (v0.2)
- Visual em vidro, ao estilo Apple Home, com 5 fundos à escolha (Pinheiro, Noite, Aurora, Areia, Oceano).
- Início: saudação, data e hora, chips de estado (tempo, temperatura interior, luzes, estores, portas, fechaduras, música), faixa de cenas e cartões de divisão com temperatura, humidade e botão para as luzes.
- Divisão: mosaicos agrupados por tipo. Toque liga/desliga; toque longo (ou clique direito) abre o detalhe.
- Detalhe: luminosidade e cor das luzes, posição dos estores, anel do termóstato com modos, controlos de multimédia e volume, câmara, sensores.
- Dock inferior: Casa e Cenas.

## Roadmap
Modo de edição, layouts, câmaras, clima, meteorologia, ecrã de descanso, temas.
