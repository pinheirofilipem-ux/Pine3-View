# Pine3 View

Painel pessoal para o Home Assistant, inspirado no Apple Home. Organiza a casa
automaticamente por divisão (Áreas → Dispositivos → Entidades), sem YAML.
Código original, feito de raiz.

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

## Definições
Botão ⚙ no canto superior direito:
- **Aparência**: cor dos cartões, cor de destaque e fundo, com pré-visualização ao vivo.
- **Início**: meteorologia, pessoas, estado da casa, cenas, cartões de resumo e relógio 12/24 h.
- **Divisões**: ordenar e esconder divisões; voltar a mostrar dispositivos escondidos.
- **Navegação**: botões do dock (Divisões, Cenas, Segurança).
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
Modo de edição (arrastar, mudar tamanho), ecrã de descanso, layouts alternativos.
