# Pine3 View, versão nativa (experimental)

Uma app Android **nativa** (sem WebView) que mostra a mesma casa do painel web do Pine3 View. Serve para comparar a
fluidez com o WebView e junta o que o *Pine3 Wallpanel* já faz num tablet de parede.

O código desta pasta é **independente** da integração (`custom_components/`): a instalação por HACS não é afetada.

## O que faz
- **Ligação ao Home Assistant** por WebSocket (token de acesso de longa duração). Lê o mesmo modelo da casa do painel web
  (`pine3_view/home`, `pine3_view/subscribe`) e grava as definições de volta (`pine3_view/settings`), por isso o que mudas aqui vale
  também no painel web. O endereço por omissão é `http://homeassistant.local:8123/pine3-view`.
- **Ecrãs**: Início (as 4 disposições), Divisões, uma divisão, Cenas, Clima, Segurança e **Casa 3D** (canvas, com a planta desenhada no
  painel web). Disposição em grelha ou em lista, divisões e dispositivos de tamanho duplo.
- **Cartões e janelas**: luzes (cor e luminosidade), estores, clima (anel), multimédia, ventoinha, fechadura, **aspirador** (comandos, rotinas,
  potência, divisões a limpar e mapa), **câmara** (o cartão mostra a última imagem; o vídeo só corre dentro da janela), tempo com previsão
  (entidade do HA ou Open-Meteo) e **avisos do IPMA**.
- **Campainha**: janela com a última imagem da câmara e a foto do toque, "Ver câmara" e abrir a porta (2 toques). A entidade que acorda o
  ecrã é configurável (por omissão `event.entry_hallway_comelit_intercom_doorbell`, `ring`, 120 s).
- **Modo de edição** (menu ⋮): arrastar para ordenar, esconder, tamanho duplo e repor o que foi escondido.
- **Definições**: o mesmo formato do painel web (Aparência, Início, Divisões, Entidades, Navegação, Sistema) mais uma secção **App** com o
  que é só do dispositivo (ligação, ecrã, campainha, permissões, escala da interface). As alterações de aspeto aplicam-se na hora.
- **Aspeto**: 26 temas, português/inglês, todos os ícones do Material Design Icons (7447), dock "Liquid Glass" com lente elástica,
  animações elásticas por toda a app (desligam-se com "Animações") e **ecrã de descanso**.
- **Ecrã de parede**: a câmara frontal deteta movimento e acorda o ecrã, que desliga sem luz (administrador de dispositivo) depois de X
  segundos, com fade para preto e de volta; toques em qualquer janela contam como atividade; arranque automático; a ligação fica viva com
  o ecrã desligado. Atalho para as definições: dois dedos premidos 2 s.

**Fora do âmbito por agora**: editor de planta (continua no painel web), formato estreito/telemóvel, vídeo MJPEG, modos 3D leve e planta 2D.

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
