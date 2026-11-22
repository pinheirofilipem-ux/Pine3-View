# Pine3 View, versão nativa (experimental)

Uma app Android **nativa** (sem WebView) que mostra a mesma casa do painel web do Pine3 View. Serve para comparar a fluidez com o
WebView e junta, num tablet de parede, o que o *Pine3 Wallpanel* já faz: acordar com o movimento, desligar o ecrã sem luz e ligar-se ao
Home Assistant.

O código desta pasta é **independente** da integração (`custom_components/`): a instalação por HACS não é afetada.

> Estado: experimental. O código é compilado no CI e testado à mão num OPPO Pad SE (Android 13, ColorOS, 90 Hz); outros dispositivos
> podem comportar-se de forma diferente.

## Ligação ao Home Assistant
- WebSocket com token de acesso de longa duração. Lê o **mesmo modelo da casa** do painel web (`pine3_view/home`, `pine3_view/subscribe`)
  e grava as definições de volta (`pine3_view/settings`), por isso o que mudas aqui vale também no painel web.
- Endereço por omissão: `http://homeassistant.local:8123/pine3-view` (só a origem conta para a ligação). O Android muitas vezes não
  resolve nomes `.local`: se falhar, usa o IP do HA.
- Religa sozinha, mantém-se viva com o ecrã desligado e tem um **teste de ligação** passo a passo (DNS, WebSocket, token, Pine3 View).

## Ecrãs
- **Início** com as 4 disposições do painel web (clássica, topo, colunas, barra lateral), com as medidas do CSS (px = dp, `vw`, `clamp`).
- **Divisões**, **uma divisão** (filtros por tipo, cenas e dispositivos), **Cenas**, **Clima**, **Segurança** e **Casa 3D**.
- **Casa 3D** desenhada num canvas com a planta que desenhaste no painel web (posições, formas livres, pisos, luzes e equipamentos):
  as divisões brilham com a cor das luzes acesas, arrastas para rodar, pinça para aproximar, tocas numa luz para a ligar e numa
  divisão para a abrir. O **editor de planta** continua só no painel web.
- Grelha ou **lista**, divisões e dispositivos de **tamanho duplo**.
- **Modo de edição** (menu ⋮): arrastar para ordenar, esconder, tamanho duplo e repor o que foi escondido. Grava as mesmas definições
  do painel web.
- Todos os ícones do Material Design Icons (7447, os mesmos do HA), 26 temas, português e inglês, escala da interface.

## Cartões e janelas
- **Luzes** (luminosidade e cor, como o cartão do painel web), estores, **clima** (anel e modos), multimédia, ventoinha, fechadura e o
  resto das entidades de cada dispositivo. Os estados dos botões (cartões e Casa 3D) refletem sempre o estado real.
- **Aspirador**: cartão largo com comandos rápidos e janela com rotinas, potência, escolha das divisões a limpar e o mapa (Roborock).
- **Câmara**: o cartão mostra só a última imagem guardada (nunca liga a câmara); o vídeo corre dentro da janela, com abrir a porta
  (2 toques) e +2 minutos. Há um marcador de carregamento enquanto a imagem não chega.
- **Tempo** (entidade do HA ou Open-Meteo para a localização da casa): cartão e janela de previsão, com as horas a deslizar para a
  esquerda como no iPhone, 7 dias com barra de temperaturas, nascer e pôr do sol, humidade, vento, UV, risco de incêndio e **avisos do IPMA**.
- **Campainha**: pop-up com a última imagem da câmara da porta; a foto do toque substitui-a assim que chega. Tem "Ver câmara" e abrir a
  porta. A porta segue as regras do painel web (as escolhidas para a câmara, `release_entity` da campainha, fechaduras da divisão) e
  pode ser escolhida em "Escolher portas". Há interruptor do pop-up e botão **Testar a campainha** nas definições da App.
  Se o ecrã estiver em screensaver ou apagado, a campainha acorda-o e abre o pop-up.

## Ecrã de parede
- A câmara frontal deteta movimento e **acorda o ecrã**, que **desliga sem luz** (administrador de dispositivo) ao fim de X segundos, com
  fade para preto e de volta, à mesma velocidade. Toques em qualquer janela (incluindo as definições) contam como atividade.
  Arranque automático depois de reiniciar.
- **Campainha que acorda o ecrã**: uma entidade configurável (por omissão `event.entry_hallway_comelit_intercom_doorbell`, só em `ring`,
  durante 120 s).
- **Screensaver** (secção própria nas definições, com a entrada e o bloqueio juntos):
  - *Entrada* em segundos (barra até 120 s) e *bloqueio do ecrã* (3 a 180 s). O screensaver entra sempre **antes** do bloqueio e fica
    **por cima de tudo**; ao passar para o bloqueio o vídeo mantém-se até o ecrã ficar negro.
  - *Clássico*: relógio escurecido com deriva lenta.
  - *Vídeo*: clipes em ecrã inteiro com hora e data desenhadas à mão (sem cortes), que trocam de lado ao acaso a cada clipe e a cada
    90 s, e fade entre clipes. Ajuste do vídeo (pela altura sem esticar, preencher ou inteiro) e qualidade.
  - *Fontes*: **uma ou várias pastas do tablet** (escolhidas no seletor de pastas do Android, ficheiros `.mov`/`.mp4`, com as subpastas), um **pacote zip** descarregado
    uma vez para o tablet, uma lista `.json`/`.txt` de endereços, endereços extra e, opcionalmente, o ficheiro de recursos da Apple TV.
  - *Lista de clipes* com o tamanho de cada um e interruptor por clipe. Por omissão ignora intro, loop, outro e ficheiros pequenos.
  - Se o leitor normal do Android recusar um clipe (HEVC 4K), usa um descodificador alternativo (MediaCodec).
  - *Fechar janelas após* (0 a 120 s) e *sensibilidade ao movimento* da câmara frontal.
- As janelas normais (detalhe de dispositivo, previsão, escolha de portas) fecham sozinhas ao fim do tempo definido em Screensaver
  (por omissão 10 s, 0 = nunca); a câmara e a campainha ficam abertas.
- Atalho para as definições: dois dedos premidos durante 2 s, ou o menu ⋮.

## Definições (mesmo formato do painel web)
Aparência, Início, Divisões, Entidades, **Screensaver**, Planta 3D, Navegação, Sistema, mais uma secção inicial **App** com o que é só do
dispositivo (ligação, tamanho da interface, campainha e teste, arranque, permissões). As alterações de aspeto aplicam-se na hora, sem
voltar ao topo, e o botão Guardar fica ativo a qualquer alteração (incluindo a língua e o endereço/token).
- **Estilo do vidro** (Aparência): *Atual*, *Suave* ou *Liquid Glass* (aro de luz, brilho, lente com relevo e, nas janelas, desfoque real
  em Android 12 ou mais recente).
- Cor de fundo e paletas (incluindo pastel), temas, animações, língua (PT/EN), disposição (grelha/lista).

## Dock "Liquid Glass"
A lente de vidro desliza com mola por baixo do separador ativo e estica como uma gota. Também se **agarra e arrasta** com o dedo: cresce,
segue o dedo e assenta no separador mais próximo. Animações elásticas por toda a app (desligam-se com "Animações").

## Fora do âmbito
Editor de planta (continua no painel web), formato estreito/telemóvel, vídeo MJPEG, modos 3D leve e planta 2D, e leitor de música
(Apple Music/AirPlay não está implementado).

## Num tablet ColorOS (OPPO)
O ColorOS mata apps em segundo plano com mais agressividade. Convém: bateria da app sem restrições, arranque automático ligado, app
bloqueada nos recentes, poupança de energia inteligente desligada e, nas permissões, desligar "remover permissões se a app não for usada".

## Compilar
```bash
cd android
./gradlew :app:assembleRelease
```
O APK fica em `app/build/outputs/apk/release/`. Os builds do GitHub Actions (`Android (nativo, experimental)`) publicam-no na
pre-release `android-latest`.

## Comparar com o WebView
Mesmo tablet, mesmo Home Assistant, mesmos dados: instala esta app e o *Pine3 Wallpanel* (WebView) e compara a fluidez do scroll,
o tempo até aparecer o ecrã ao acordar e o uso de memória.

## Créditos
Ícones: [Material Design Icons](https://pictogrammers.com/library/mdi/) (Apache 2.0). Tipos de letra Outfit e Figtree (SIL OFL 1.1).
