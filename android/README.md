# Pine3 View, versão nativa (experimental)

Uma app Android **nativa** (sem WebView) que mostra a mesma casa do painel web do Pine3 View. Serve para comparar a
fluidez com o WebView e junta o que o *Pine3 Wallpanel* já faz num tablet de parede.

O código desta pasta é **independente** da integração (`custom_components/`): a instalação por HACS não é afetada.

## O que a fase 1 faz
- Liga-se ao Home Assistant por WebSocket (token de acesso de longa duração) e lê o **mesmo modelo da casa** que o painel web
  (`pine3_view/home` e `pine3_view/subscribe`): divisões, dispositivos, cenas, tempo, pessoas e as definições (cores, o que mostrar).
  Não edita nada: os editores continuam no painel web.
- **Casa** (relógio, estado da casa com *Acender/Apagar luzes*, tempo, pessoas, cenas e cartões das divisões), **Divisões**,
  **uma divisão** (filtros por tipo e cartões dos dispositivos) e **Cenas**. Tocar nos cartões liga/desliga.
- Os cartões atualizam-se sozinhos quando os estados mudam, sem reconstruir a página.
- **Ecrã de parede**: a câmara frontal deteta movimento e acorda o ecrã, que desliga sem luz (administrador de dispositivo) depois
  de X segundos; a ligação ao HA fica viva com o ecrã desligado; arranque automático; fade suave ao acordar.
- **Campainha**: uma entidade que acorda o ecrã (por omissão `event.entry_hallway_comelit_intercom_doorbell`, só quando `ring`,
  120 s), da integração Comelit.
- Atalho para as definições: dois dedos premidos 2 s, ou o botão da engrenagem no início.

Ainda não tem: casa 3D e planta (fase 2), ecrã nativo da campainha com câmara (fase 3), previsão do tempo, detalhe dos dispositivos
(luminosidade, cor, clima...).

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
