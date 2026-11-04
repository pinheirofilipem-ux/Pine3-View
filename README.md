# Pine3 View

Painel pessoal para o Home Assistant, inspirado no Apple Home. Organiza a casa
automaticamente por divisão (Áreas → Dispositivos → Entidades), sem YAML.
Código original, feito de raiz.

## Capturas de ecrã

| Início | Casa 3D |
|---|---|
| ![Início com relógio, tempo, estado da casa, cenas e divisões](docs/screenshots/home.jpg) | ![Casa 3D com dois pisos, uma sala em L e dispositivos no sítio](docs/screenshots/house-3d.jpg) |
| **Divisão** | **Previsão do tempo** |
| ![Vista de uma divisão com os dispositivos agrupados por tipo](docs/screenshots/room.jpg) | ![Previsão das próximas horas e dos próximos dias](docs/screenshots/forecast.jpg) |
| **Planta: formas livres** | **Planta: dispositivos** |
| ![Editor da planta a pintar quadrículas para uma sala em L](docs/screenshots/plan-shapes.jpg) | ![Editor da planta a pôr dispositivos e escolher o tipo](docs/screenshots/plan-devices.jpg) |
| **Casa 3D, rés-do-chão** | **Modo de edição** |
| ![Casa 3D só com o rés-do-chão](docs/screenshots/house-3d-ground-floor.jpg) | ![Modo de edição com cartões para esconder e duplicar](docs/screenshots/edit-mode.jpg) |
| **Definições: aparência** | **Definições: entidades** |
| ![Cores dos cartões, cor de destaque, fundo, idioma e disposição](docs/screenshots/settings-appearance.jpg) | ![Escolher que entidades aparecem](docs/screenshots/settings-entities.jpg) |
| **Este dispositivo (ecrã inteiro)** | **Lista, em inglês** |
| ![Opções por dispositivo: esconder a barra lateral, manter o ecrã ligado, ecrã inteiro](docs/screenshots/settings-device.jpg) | ![Disposição em lista com o painel em inglês](docs/screenshots/list-english.jpg) |
| **Ecrã de descanso** | |
| ![Relógio grande do ecrã de descanso](docs/screenshots/screensaver.jpg) | |

No telemóvel:

| Início | Detalhe de uma luz | Casa 3D |
|---|---|---|
| ![Início no telemóvel](docs/screenshots/phone-home.jpg) | ![Folha de detalhe com luminosidade e cores](docs/screenshots/phone-light.jpg) | ![Casa 3D no telemóvel](docs/screenshots/phone-3d.jpg) |

*Capturas feitas com uma casa de exemplo.*

## Instalar com HACS
1. No Home Assistant abre **HACS** → menu **⋮** → **Repositórios personalizados**.
2. Repositório: `https://github.com/pinheirofilipem-ux/pine3-view`, categoria **Integração** → **Adicionar**.
3. Procura **Pine3 View**, abre e carrega em **Transferir**.
4. Reinicia o Home Assistant.
5. **Definições → Dispositivos e serviços → Adicionar integração → Pine3 View**.
6. Abre **Pine3 View** na barra lateral.

Requer Home Assistant 2024.7 ou mais recente. Para atualizar: o HACS mostra a nova versão; transfere,
reinicia e faz um refresh forçado ao browser (Ctrl+Shift+R).

> O HACS só consegue ler repositórios **públicos**, e instala a partir do ramo principal (`main`)
> ou da última *release* do GitHub.

### Instalação manual
Copia `custom_components/pine3_view` para `/config/custom_components/`, reinicia e adiciona a integração.

## Funcionalidades
- **Início**: relógio, tempo (toca para ver a previsão), pessoas, estado da casa, cenas, cartões de divisão e resumo (Clima, Segurança, A tocar).
- **Tempo**: usa a entidade de tempo do Home Assistant ou, se não houver, a [Open-Meteo](https://open-meteo.com) para a localização da casa (sem chave). Previsão das próximas 12 horas e 7 dias.
- **Casa 3D**: cada divisão é um bloco, empilhado por piso (pisos do HA). As divisões acesas brilham, as luzes aparecem como pontos que podes ligar/desligar, arrasta para rodar, toca numa divisão para a abrir.
- **Planta 3D** (Definições → Planta 3D, ou «Editar planta» na Casa 3D): editor 2D visto de cima, um piso de cada vez. Desenha cada divisão como um retângulo na grelha, arrasta para mover, puxa o canto para redimensionar, muda de piso ou cria pisos novos. Com **Pintar quadrículas** dás qualquer forma de ângulos retos a uma divisão (L, U, T…): tocas ou arrastas nas quadrículas, e pintar por cima de outra divisão passa-lhe essas quadrículas; **Apagar quadrículas** tira-as. Na Casa 3D o chão segue a forma e as paredes só aparecem no contorno. Com **Dispositivos** colocas cada entidade no sítio certo da planta (escolhes a divisão, tocas no dispositivo e depois na planta; arrastas para mover). O tipo é detetado automaticamente — luz de teto, candeeiro, fita LED, spot, TV, Apple TV, HomePod, coluna, ar condicionado, estore, porta, janela, tomada, sensor, câmara… — e podes mudá-lo. Na Casa 3D as luzes brilham no sítio (as de teto no alto, a fita LED como uma faixa) e os outros aparelhos aparecem como ícones que abrem o detalhe. A Casa 3D usa esta planta; as divisões por colocar são arrumadas automaticamente. Há também «Gerar planta automática» como ponto de partida.
- **Entidades** (Definições → Entidades): lista todas as entidades de cada divisão, com pesquisa. Esconde mosaicos que não queres ou mostra entidades secundárias (o interruptor de uma TV, a potência de uma tomada, sensores de diagnóstico) como mosaicos próprios.
- **Apple TV e HomePod** (integração *Apple TV* do HA): ícones próprios e a app/música que está a tocar.
- **Modo de edição** (lápis ✎): arrasta para reordenar divisões e dispositivos, toca em − para esconder, nas setas para duplicar o tamanho (um cartão de divisão duplo mostra atalhos para os dispositivos).
- **Disposição**: Grelha ou Lista.
- **Ecrã de descanso**: relógio grande e escurecido após 1–30 min sem toques.
- **Ecrã inteiro e modo quiosque**: botão ⛶ no topo para o ecrã inteiro do browser; em *Definições → Início → Este dispositivo* escondes a barra lateral do Home Assistant e manténs o ecrã ligado (só nesse dispositivo). Abrir o painel com `?kiosk` no endereço (ex.: `http://homeassistant.local:8123/pine3-view?kiosk`) também esconde a barra lateral — ideal para um tablet na parede.
- **Idioma**: Português ou English, escolhido nas definições.

## Definições
Botão ⚙ no canto superior direito:
- **Aparência**: idioma, disposição, cor dos cartões, cor de destaque e fundo, com pré-visualização ao vivo.
- **Início**: meteorologia e fonte do tempo, pessoas, estado da casa, cenas, cartões de resumo, relógio 12/24 h e ecrã de descanso.
- **Divisões**: ordenar, esconder e escolher o tamanho de cada divisão na Casa 3D; voltar a mostrar dispositivos escondidos.
- **Navegação**: botões do dock (Divisões, Casa 3D, Cenas, Segurança).
- **Sistema**: versão, contagens, reconstruir agora, repor predefinições.

As definições ficam guardadas no Home Assistant e valem para todos os dispositivos.

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
Plantas 3D com posições desenhadas à mão, câmaras no início, leitor Spotify.

## Licença
MIT (ver `LICENSE`). As fontes Outfit e Figtree estão sob a SIL Open Font License 1.1 (ver `NOTICE`).
