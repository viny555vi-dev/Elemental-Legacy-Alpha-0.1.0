# Elemental Legacy — estudo de referência de combate elemental

**Branch:** `vfx/custom-geometry-engine`  
**Data do estudo:** 2026-11-18  
**Objetivo:** traduzir os princípios de combate de *Elements Divided*, *RUMBLE* e a coreografia de *Avatar: A Lenda de Aang* para um mod Minecraft com mundo persistente e servidores autoritativos.

## 1. Identificação do jogo do link enviado

A correspondência mais forte encontrada para a referência enviada é **Elements Divided**, desenvolvido por **Loco Motion Devs** e publicado por **Fast Travel Games**. O estúdio confirma que o jogo trabalha os quatro elementos, com gestos para invocar ataques/defesas e combate em arena; a loja oficial informa lançamento em 23 de abril de 2025. Há também um vídeo público intitulado **“Impaling My Friend with an Earthbending PRISON (Elements Divided VR Gameplay)”**, que demonstra uma prisão terrestre usada em combate.

**Nota de verificação:** a página do post do Instagram fornecido não pôde ser carregada pela ferramenta de pesquisa. A identificação acima é a correspondência mais forte encontrada por cruzamento de título, data e conteúdo; não declarar que o conteúdo exato do post foi visualizado. O vídeo específico de prisão e as páginas oficiais do jogo são referências verificáveis.

## 2. Referências que passam a orientar o projeto

### Referência principal — Elements Divided

- Site do estúdio: https://www.locomotiondevs.com/
- Página oficial do jogo: https://fasttravelgames.com/games/elements-divided
- Steam: https://store.steampowered.com/app/2805770/Elements_Divided/
- Meta Quest: https://www.meta.com/experiences/elements-divided/9471144046261462/
- Wiki de movimentos (comunitária, página Earth marcada como desatualizada): https://elementsdivided.wiki.gg/wiki/Earth
- Fundamentos dos gestos: https://elementsdivided.wiki.gg/wiki/Basics
- Vídeo geral oficial/reveal: https://www.youtube.com/watch?v=GSG93yXRVfs
- Demonstração de prisão terrestre em combate: https://www.youtube.com/watch?v=9GH8nZxEge4

**O que estudar no jogo:**
- **Boulder:** massa de rocha convocada do solo; o jogo permite carregamento e a indicação de carga é o tremor do solo. A elevação parte do chão e o projétil é uma massa individual.
- **Earth Wall:** parede larga e ancorada no terreno. A elevação é comandada por ambas as mãos, não por uma linha de partículas. No jogo original pode decair; no Elemental Legacy a muralha de construção deve permanecer até mineração/destruição, conforme decisão explícita do usuário.
- **Earth Spear / Earth Saw:** mesma origem de massa no terreno, com transformação da forma conforme o comando. Servem de referência para rocha que muda de geometria e não para efeitos genéricos.
- **Earth Punch:** golpe físico que pode atingir e lançar objetos de terra, inclusive desprender a parede do solo. A lição é que estruturas e projéteis precisam ter estado físico e interações entre si.
- **Earth Pillar / Earth Spike:** a massa sobe de baixo para cima, saindo do terreno e criando uma coluna/crista de impacto; não é uma malha que aparece por inteiro no ar.
- **Earth Trap:** contenção anunciada por tremor/dust antes do fechamento; o alvo não deve ser teletransportado para dentro de blocos.
- **Earth Armor:** defesa real: bloqueia ataques, desacelera o usuário, reduz knockback e reduz a capacidade de salto. Visual e atributo devem existir juntos.
- **Ground Slam:** impulso para baixo com peso corporal e impacto; a direção do olhar pode influenciar a trajetória. É uma referência melhor para a preparação e impacto sísmico do que uma onda circular decorativa.
- **Meteor / Death Trap:** ataques máximos devem avisar o ambiente antes do impacto: tremor, poeira, massa grande em formação, área previsível e consequência forte. O aviso visual é parte do gameplay, não decoração.

A página de Earth da wiki informa que está desatualizada e deve ser usada como índice de movimentos, não como autoridade de balanceamento. Validar cada mecânica em vídeos recentes e na build do jogo quando possível.

### Referência secundária de física — RUMBLE

- Steam: https://store.steampowered.com/app/890550/RUMBLE/
- Página oficial do estúdio: https://www.bucketheadentertainment.com/
- Wiki de estruturas: https://rumble.wiki.gg/wiki/Structures
- Tutorial com todos os movimentos de terra: https://www.youtube.com/watch?v=qm2qcbBo06c
- Partida completa com combate e combos: https://www.youtube.com/watch?v=97Fhw1CK8rE
- Gameplay longo: https://www.youtube.com/watch?v=GKWcq5-8qmw

**Princípios mecânicos úteis do RUMBLE:**
- As estruturas são originadas do solo e dependem de contato com terra; não podem ser invocadas livremente no ar ou em material incompatível.
- Cada forma possui massa, peso e potência distintos (disco, esfera, pilar, cubo, parede e rocha grande).
- Estruturas colidem e podem quebrar umas às outras conforme potência. Estruturas de potência igual podem se destruir no choque; a mais forte pode quebrar a mais fraca e continuar.
- Parede é ampla, ancorada e resistente; uma estrutura móvel suficientemente forte pode derrubá-la.
- Gestos possuem intenção, postura e direção legíveis. Os movimentos podem ser combinados e contragolpeados.

O RUMBLE não é o jogo principal de referência visual, mas sua modelagem de peso, colisão, resistência e contrapoder é especialmente útil para o que o Minecraft precisa executar no servidor.

### Referência de coreografia — Avatar: A Lenda de Aang

- Earthbending: https://avatar.fandom.com/wiki/Earthbending
- Earthbending training / Bitter Work: https://avatar.fandom.com/wiki/Earthbending_training
- Exemplo narrativo de muralha e combate: episódio “The Chase” (transcrição): https://avatar.fandom.com/wiki/Transcript:The_Chase

A dobra de terra deve parecer enraizada, firme e decidida: base aberta, joelhos flexionados, quadril baixo, transferência do peso dos pés pelo quadril e tronco para braços ou pernas. O impacto precisa ser comunicado pelo corpo antes de ser comunicado por partículas.

## 3. Matriz de tradução para as seis técnicas do Elemental Legacy

| Técnica Elemental Legacy | Base de referência | O que preservar/adaptar |
|---|---|---|
| Rochas Arremessadas | Elements Divided Boulder + RUMBLE Ball / Boulder | Extração do solo, tremor de carga, massa individual, impulso no momento da soltura, rotação, gravidade, colisão e fragmentação no impacto. O número de projéteis pode ser três, mas cada um precisa ter trajetória e impacto independentes. |
| Muralha de Pedra | Elements Divided Earth Wall + RUMBLE Wall | Formação de baixo para cima, base ancorada, massa e integridade, colisão com projéteis/entidades, capacidade de bloquear e de ser destruída. **No mod é permanente** até mineração/destruição; não copiar o timer de decay do jogo de referência. |
| Prisão de Rocha | Elements Divided Earth Trap / Death Trap + Avatar Earth Trap | Alvo selecionado; aviso por tremor e poeira; fissuras convergem no alvo; pilares e placas sobem em sequência e fecham espaço sem sobrepor o corpo; contenção mecânica real; saída por quebra/fuga válida. |
| Tremor Sísmico | Elements Divided Ground Slam + Earth Pillar + Avatar fissures | Preparação ancorada; impacto no chão; frente terrestre avança pelo terreno; cristas/placas sobem na sequência da onda; dano e knockback no instante em que alcança o alvo. Não deve deixar cicatriz permanente, pois sua função é pulso tático curto. |
| Armadura de Pedra | Elements Divided Earth Armor + Avatar rock armor training | Revestimento em placas encaixadas no corpo e preso às articulações; proteção mensurável, redução de knockback e mobilidade reduzida. Não é ataque radial. Duração e desgaste do jogo devem ser adaptados a estado defensivo do Minecraft. |
| Terremoto | Avatar fissures/tectonic rift + Elements Divided Ground Slam/Meteor/Death Trap | Canalização longa; aviso por tremor/dust; ondas distintas; rachaduras ramificadas que realmente alteram o terreno e permanecem depois do VFX. A área precisa ser limitada, deterministicamente selecionada e protegida contra containers, block entities, bedrock e chunks não carregados. |

## 4. Coreografia obrigatória por princípios

### Não se deve animar o resultado antes da causa

- O pé/joelho/quadril inicia o movimento; o braço conclui. A massa só começa a se mover quando o gesto atinge o ponto de comando.
- Tremor e poeira são sinais de tensão e carga antes de a rocha sair do solo.
- Rocha grande não acelera instantaneamente nem muda direção sem impulso ou comando; deve mostrar inércia.
- Parede e prisão são construídas por segmentos, com encaixe e impacto; não devem escalar do zero numa única interpolação.
- Terremoto é uma propagação espacial. O ponto mais distante não pode reagir ao mesmo tempo que o epicentro.
- Armadura é equipamento corporal: placas são transformadas em coordenadas locais dos ossos/partes do jogador e continuam alinhadas em caminhada, giro, pulo e nado.

### Consequências físicas devem ser legíveis

- Entidades recebem dano/knockback uma vez por colisão ou por frente de onda, não por tick enquanto o mesh atravessa.
- Rochas, muralhas, pilares e armaduras têm integridade/estado, e podem quebrar ou ser quebrados conforme a técnica.
- Toda estrutura sólida tem colisão no servidor e presença no mundo; o VFX representa exatamente o objeto do servidor.
- Cicatriz de terremoto é dado de mundo persistente, não ribbon client-side e não partícula temporária.
- Efeitos temporários e construções permanentes nunca devem usar a mesma rotina de limpeza.

## 5. Critérios de produção no Elemental Legacy

1. Reproduzir em vídeo as seis técnicas da referência principal, anotando os tempos de preparação, subida, deslocamento, impacto e recuperação.
2. Separar as técnicas em estados físicos e linha temporal única: servidor, corpo, mesh e áudio leem os mesmos eventos.
3. Usar a referência de peso/colisão do RUMBLE para criar massa, integridade, colisão e interação entre estruturas.
4. Traduzir as poses de VR para animação de corpo inteiro em terceira pessoa: base, pernas, quadril, torso, ombro, cotovelo e mão. Não copiar o input VR literalmente.
5. Reproduzir a qualidade visual em Minecraft com geometria facetada e textura de blocos coerente, mas preservar o voxel look; não importar assets, nomes ou código de terceiros.
6. Fazer primeiro uma técnica completa como vertical slice — Rochas Arremessadas — com vídeo, servidor, colisão, alvos e terreno. Só avançar depois que o pipeline for provado. Depois construir estruturas e ondas sobre a mesma arquitetura.
7. A muralha permanente e as cicatrizes permanentes do Terremoto são requisitos próprios do Elemental Legacy e prevalecem sobre temporizadores encontrados nos jogos de referência.
8. Aprovação só com captura de jogo real + comprovação de estado do servidor. Logs de animação, contagem de partículas e build verde não bastam.

## 6. Fontes de apoio

- Loco Motion Devs (desenvolvedor; data e descrição do jogo): https://www.locomotiondevs.com/
- Fast Travel Games (publicador; quatro elementos, modos e progressão): https://fasttravelgames.com/games/elements-divided
- Steam (lançamento, desenvolvedor/publicador e descrição): https://store.steampowered.com/app/2805770/Elements_Divided/
- Elements Divided Earth moves (wiki comunitária, marcada como desatualizada): https://elementsdivided.wiki.gg/wiki/Earth
- Elements Divided Basics (gestos e comandos, wiki comunitária): https://elementsdivided.wiki.gg/wiki/Basics
- Elements Divided Version History (mudanças nos sistemas de terra e física): https://elementsdivided.wiki.gg/wiki/Version_History
- UploadVR (anúncio e descrição independente): https://www.uploadvr.com/elements-divided-avatar-like-vr-multiplayer-action-game/
- Elements Divided Earth Prison gameplay: https://www.youtube.com/watch?v=9GH8nZxEge4
- RUMBLE structures (massa, potência, peso e colisões): https://rumble.wiki.gg/wiki/Structures
- RUMBLE all earthbending spells: https://www.youtube.com/watch?v=qm2qcbBo06c
- Avatar Earthbending: https://avatar.fandom.com/wiki/Earthbending
