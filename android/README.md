# Pine3 View, versão nativa (experimental)

Uma app Android **nativa** (sem WebView) que mostra a mesma casa do painel web do Pine3 View. Serve para comparar a
fluidez com o WebView e junta o que o *Pine3 Wallpanel* já faz num tablet de parede.

O código desta pasta é **independente** da integração (`custom_components/`): a instalação por HACS não é afetada.

## O que faz agora
- Liga-se ao Home Assistant por WebSocket (token de acesso de longa duração) e lê o **mesmo modelo da casa** que o painel web
  (`pine3_view/home` e `pine3_view/subscribe`): divisões, dispositivos, cenas, tempo, pessoas e as definições (cores, o que mostrar).
  Não edita nada: os editores continuam no painel web.
- **Casa** (relógio, estado da casa com *Acender/Apagar luzes*, tempo, pessoas, cenas e cartões das divisões), **Divisões**,
  **uma divisão** (filtros por tipo e cartões dos dispositivos) e **Cenas**. Tocar nos cartões liga/desliga.
- **Todos os ícones** do Material Design Icons (7447, os mesmos do Home Assistant), desenhados nativamente; respeita o ícone que cada
  entidade define no HA.
- **Aspeto e idioma** (Definições → Aspeto e idioma): os 26 temas do painel web (fixo, ou um de dia e outro de noite pelo sol), português
  ou inglês, cores dos cartões e de destaque, e o que mostrar no início. As escolhas são gravadas no HA e valem também para o painel web.
- **Dock "Liquid Glass"**: a lente de vidro desliza por baixo do separador ativo com mola e estica como uma gota (como no painel
  web); as páginas entram com fade, subida e desfoque. Respeita a opção "Animações" do painel.
- **Cartões de resumo** no início: Clima, Segurança, Aspirador e A tocar.
- **Testar ligação** (Definições): diz em que passo falha (DNS, WebSocket, token, Pine3 View) e o que fazer.
- Os cartões atualizam-se sozinhos quando os estados mudam, sem reconstruir a página.
- **Ecrã de parede**: a câmara frontal deteta movimento e acorda o ecrã, que desliga sem luz (administrador de dispositivo) depois
  de X segundos; a ligação ao HA fica viva com o ecrã desligado; arranque automático; fade suave ao acordar.
- **Campainha**: uma entidade que acorda o ecrã (por omissão `event.entry_hallway_comelit_intercom_doorbell`, só quando `ring`,
  120 s), da integração Comelit.
- Atalho para as definições: dois dedos premidos 2 s, ou o botão da engrenagem no início.

**Paridade com o painel web (em curso)**
- Feito: as 4 disposições do início (clássica, topo, colunas, barra lateral) com as medidas do CSS (px = dp, `vw` e `clamp`),
  cartões de divisão, de dispositivo, de clima (com anel) e de resumo, ecrãs **Clima** e **Segurança**, dock configurável (e que se
  adapta ao espaço), painel de detalhes dos dispositivos (luz, estore, clima, multimédia, ventoinha, aspirador, fechadura), tempo com
  previsão (horas e dias), pessoas só quando ativas.
- Também: cartão e visualizador de **câmara** (imagem atualizada ~1 vez por segundo, abrir porta com 2 toques, +2 min), painel de detalhes em duas colunas com leituras e controlos, previsão com barras de temperatura e **escala da interface** (Definições) para igualar o tamanho do painel web.
- Falta: casa 3D e editor de planta, modo de edição (arrastar e esconder), ecrã de descanso, avisos do IPMA, câmaras e pop-up da campainha,
  detalhe do aspirador (mapa e divisões), disposição em lista, formato estreito/telemóvel, divisões e dispositivos "grandes".

Ainda não tem: casa 3D e planta (fase 2), ecrã nativo da campainha com câmara (fase 3), previsão do tempo, detalhe dos dispositivos
(luminosidade, cor, clima...), disposições do início diferentes de "top", e a edição (cartões, ordem, esconder).

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
