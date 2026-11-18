# Elemental Legacy — reconstrução do elemento Terra

**Status:** especificação obrigatória de implementação e aceite  
**Branch de trabalho:** `vfx/custom-geometry-engine`  
**Alvo:** Minecraft Java 26.3 / NeoForge 26.3.0.95 / Java 25

Este documento substitui, para o elemento Terra, as fichas antigas quando houver conflito com as decisões abaixo. Não altera o elemento Água, a branch `main` nem os critérios dos outros elementos.

## 1. Princípio de direção

Terra não é fogo pintado de cinza, nem partículas de bloco seguindo uma curva. Sua assinatura é **massa, resistência, compressão, apoio no solo, inércia e impacto**.

Referências de direção:
- A coreografia de dobra de terra em *Avatar: A Lenda de Aang* deve parecer enraizada: base larga, joelhos flexionados, centro de massa baixo, movimentos decididos e transferência de força do chão pelo quadril e tronco até braços/pernas. A postura deve preparar a ação, não ser um gesto de mão isolado.
- O material precisa parecer retirado e compactado do ambiente. A rocha não deve parecer uma esfera pronta escondida no chão ou um objeto que aparece do nada.
- O vídeo de referência enviado pelo usuário (`Minecraft_firebending_animation_…_6ebb44b8.mp4`, 7,93 s) é referência de **encenação e intensidade**, não de aparência elemental: preparação corporal identificável, formação volumétrica, trajetória legível, impacto amplo, detritos e consequência visual no cenário. Terra deve traduzir isso em peso e fratura, sem copiar a chama.

## 2. Contrato técnico obrigatório

1. **Servidor autoritativo:** custo, recarga, alvo, trajetória, colisão, dano, empurrão, estados defensivos, criação de blocos e deformação do terreno são calculados no servidor.
2. **Uma única linha temporal:** a animação do corpo, a geometria 3D, as colisões e o gameplay devem avançar a partir das mesmas fases e do mesmo instante de início. Não pode existir uma muralha visual de 9 s, blocos que somem em 3 s e uma pose do jogador que termina em 1,9 s.
3. **Física de verdade:** projéteis têm posição, velocidade, aceleração/gravity, rotação, colisão varrida e impacto. Ondas têm uma frente que percorre o terreno com velocidade definida. Estruturas têm blocos/collision shapes reais. Efeitos visuais não podem fingir uma colisão que o servidor não calculou.
4. **Animação articulada:** cada técnica tem sua própria sequência de corpo inteiro — pés, pernas, quadril, tronco, ombros, cotovelos, mãos e cabeça. Não basta alternar `ArmPose` do Minecraft ou inclinar o tronco com uma senoide genérica.
5. **Persistência explícita:** efeitos temporários, estruturas permanentes e cicatrizes de terreno são categorias diferentes. Nada permanente pode ser apagado por um timer visual ou pela limpeza de uma coleção de efeitos.
6. **Materialidade coerente:** rochas e blocos devem herdar textura/material do terreno real quando aplicável. Fragmentos precisam nascer da massa manipulada; não se usa sempre uma partícula genérica de pedra.
7. **Interrupção segura:** morte, desconexão, troca de dimensão, alvo inválido, chunk descarregado e falta de energia encerram ou cancelam o estado sem duplicar dano, energia, XP ou blocos.
8. **Proteção do mundo:** nunca substituir ar por estrutura sem validar suporte, espaço, colisões, block entities/containers, líquidos e limites do mundo. Terremoto não toca bedrock, containers, máquinas, blocos com block entity ou áreas protegidas conhecidas. Qualquer destruição de terreno deve ser limitada, determinística e registrada no teste.
9. **Evidência real:** logs de “mesh submitted” não provam qualidade. Para aprovar: vídeo/frames do jogo, logs do servidor, persistência após salvar/recarregar e teste funcional de colisão/combate.

## 3. Linha temporal e comportamento por técnica

Os tempos abaixo são metas de direção de animação, não valores de tick já implementados. Minecraft usa 20 ticks/s. A execução pode variar ligeiramente por latência, mas as fases não podem ser comprimidas em uma animação genérica de 2 segundos.

### 3.1 Rochas Arremessadas — projétil físico

**Papel:** ataque de precisão à distância.  
**Referência total:** 4,5 s, variando com o alcance real do projétil.

| Tempo | Corpo e leitura visual | Física / gameplay |
|---|---|---|
| 0,00–0,55 s | Pés se afastam, joelhos cedem, quadril baixa; braço de apoio equilibra o corpo. | O jogador ancora a postura. A câmera não precisa ser forçada. |
| 0,55–1,25 s | Uma placa do solo se parte; pequenos fragmentos se desprendem e são puxados para a mão. | O material é apresentado como massa extraída/compactada, não como rocha pronta surgindo na mão. |
| 1,25–1,80 s | Mão fecha; os fragmentos colidem e se compactam em rocha irregular, com rotação lenta e pressão. | A rocha ganha volume e massa aparente; poeira e fragmentos menores são consequência da compactação. |
| 1,80–2,05 s | Rotação curta de quadril e tronco; ombro e cotovelo conduzem um golpe direto. | O projétil recebe impulso no instante do golpe, não antes. |
| 2,05–3,80 s | Braço acompanha o lançamento; recuperação do tronco continua após a soltura. | Cada rocha tem trajetória balística, gravidade, arrasto moderado, rotação, colisão com blocos e entidades. Não atravessa parede. |
| 3,80–4,50 s | Recuperação: braço recua, postura volta ao centro. | Impacto gera lascas, poeira e dano uma única vez. A rocha se fragmenta ou cai; não desaparece sem consequência. |

**Projéteis:** manter a proposta de três rochas somente se forem lançamentos individuais e legíveis, ligeiramente escalonados, cada um com massa, trajetória e colisão próprias. Não usar três meshes paralelos presos à mesma fórmula.  
**Terreno:** não destruir blocos no lançamento básico. A origem visual usa o bloco/solo sob o jogador como referência de material.  
**Aceite:** três trajetórias independentes; cada uma para no primeiro bloco sólido ou alvo válido; no máximo um acerto por alvo/projétil; alvo atrás de parede não recebe dano.

### 3.2 Muralha de Pedra — construção permanente

**Papel:** defesa posicional e bloqueio de linha de visão.  
**Construção visual:** aproximadamente 5,5 s. **Persistência:** permanente até ser minerada/destruída; não usar timer de remoção.

| Tempo | Corpo e leitura visual | Física / gameplay |
|---|---|---|
| 0,00–0,55 s | Base abre e jogador abaixa o centro de massa; um pé ou uma mão dá o comando inicial. | Determinar plano da parede, normal, terreno e espaço livre. |
| 0,55–1,30 s | Rachaduras finas seguem o traçado no chão, do ponto central para as extremidades. | O servidor valida a faixa de 5 blocos e os apoios. |
| 1,30–3,90 s | Blocos/placas sobem em sequência centro → laterais, com encaixe de baixo para cima. O tronco e os braços acompanham a elevação como se estivessem levantando peso. | Colocar blocos reais em etapas. Cada peça ocupa espaço, tem colisão e bloqueia projéteis/visão assim que se estabiliza. |
| 3,90–4,70 s | Jogador empurra as mãos para a frente; junta/compacta as peças. | A estrutura estabiliza e passa a ser tratada como bloco do mundo. |
| 4,70–5,50 s | Poeira cai; braços descem e o jogador recupera a postura. | A muralha fica no local. Sem retração automática nem restauração por timer. |

**Dimensão-base:** faixa de 5 blocos de largura por 3 de altura, ajustada ao terreno e ao espaço disponível.  
**Integridade:** deve poder ser destruída/minerada por interação normal; não é invulnerável. O valor inicial de 30 HP da ficha antiga pode ser usado como referência de equilíbrio se a implementação tiver HP próprio.  
**Persistência:** salvar/recarregar o mundo não remove a parede. Se houver limite de paredes por jogador, a substituição de uma parede antiga deve ser uma ação explícita e visível — nunca sumir silenciosamente.  
**Aceite:** colisão física, bloqueio de projétil e linha de visão; parede continua após 12 s, 60 s e reload; não substitui container/block entity nem ocupa entidade.

### 3.3 Prisão de Rocha — contenção de alvo

**Papel:** controle de um alvo escolhido; não é uma explosão genérica à frente do conjurador.  
**Formação visual:** 6,5 s. **Persistência:** a estrutura permanece até ser quebrada/removida; o efeito de imobilização inicial é separado da prisão física.

| Tempo | Corpo e leitura visual | Física / gameplay |
|---|---|---|
| 0,00–0,50 s | Punhos fecham, cotovelos recolhem e joelhos flexionam. | Selecionar alvo válido na mira/alcance e travar o ponto de contenção no servidor. |
| 0,50–1,20 s | Solo sob o alvo vibra; fissuras radiais convergem para os pés. | Validar que há terreno e espaço ao redor do alvo. |
| 1,20–2,00 s | Pilares sobem ao redor das pernas, em sequência alternada. | A primeira coroa cria contenção inferior e limita deslocamento. |
| 2,00–3,10 s | Placas laterais sobem e se encaixam ao redor do tronco; cabeça fica livre. | A gaiola ganha colisão real; a entidade fica contida, sem bloco colocado dentro do volume do corpo. |
| 3,10–4,00 s | Mãos fecham e travam a estrutura com um golpe compacto. | Aplicar dano inicial baixo (referência: 5 HP) e breve imobilização (até 2 s em criaturas comuns; menor contra jogadores/bosses). |
| 4,00–5,20 s | O jogador mantém a mão fechada e sustenta a contenção. | A prisão continua existindo depois do fim da animação. O alvo ou aliados podem quebrá-la/minerá-la. |
| 5,20–6,50 s | Mãos abrem; pedras menores assentam e poeira cai. | Não dissolver a prisão por efeito visual. A fuga acontece por quebra real, teleporte permitido ou regra explícita de exceção para bosses. |

**Alvo:** criatura/entidade atingida pelo raycast de mira dentro do alcance, não um ponto fixo a 3 blocos do jogador. Sem alvo válido, a habilidade deve falhar com mensagem/custo coerentes, não aprisionar o ar.  
**Aceite:** alvo realmente cercado; não atravessa frestas; dano não se repete por tick; prisão sobrevive ao fim do VFX; jogador pode quebrar a estrutura; não prende entidades dentro de blocos sólidos.

### 3.4 Tremor Sísmico — pulso de área de curto alcance

**Papel:** interromper e desequilibrar inimigos próximos. É menor e mais rápido que o Terremoto.  
**Referência visual:** até 8 s, incluindo poeira/assentamento; frente ativa curta.

| Tempo | Corpo e leitura visual | Física / gameplay |
|---|---|---|
| 0,00–0,65 s | Base baixa e larga; uma mão pressiona o solo. | Registrar epicentro no chão e validar contato com terreno. |
| 0,65–1,40 s | Rachaduras discretas se espalham em anel; cascalho vibra e salta. | A onda começa no epicentro; partículas e geometria acompanham a frente real. |
| 1,40–2,00 s | Jogador ergue o braço e golpeia/empurra o solo com o punho. | O impulso inicia a propagação; não aplicar todo o dano antes da onda chegar. |
| 2,00–4,20 s | Uma frente sísmica percorre o chão até 7 blocos; placas baixas levantam em sequência e voltam a assentar. | Dano/empurrão aplicado quando a frente cruza cada alvo. Cada alvo recebe no máximo um acerto; criaturas no ar recebem efeito reduzido. |
| 4,20–6,00 s | Poeira se desloca para fora e fragmentos caem. | Breve desequilíbrio/slow em criaturas comuns; controle de jogadores mais curto. |
| 6,00–8,00 s | Recuperação da postura e dissipação da poeira. | Sem dano residual por tick e sem destruir blocos permanentemente. |

**Aceite:** a onda pode ser acompanhada visualmente do epicentro até a borda; impacto ocorre quando ela alcança o alvo; alvo em plataforma/voando não recebe o mesmo efeito que alvo no solo.

### 3.5 Armadura de Pedra — defesa vinculada ao corpo

**Papel:** defesa pessoal com custo de mobilidade e energia. Não é ataque em área.  
**Montagem:** cerca de 2,5 s. **Proteção ativa:** até 8 s. **Saída:** cerca de 1 s.

| Tempo | Corpo e leitura visual | Física / gameplay |
|---|---|---|
| 0,00–0,50 s | Braços cruzam diante do peito; joelhos flexionam e pés se fixam. | Ativar estado defensivo no servidor após validar energia. |
| 0,50–1,20 s | Fragmentos sobem dos pés e canelas, contornam as pernas e antebraços. | As peças seguem as partes do modelo; não ficam presas a uma coordenada antiga do mundo. |
| 1,20–2,00 s | Placas se encaixam nas canelas, antebraços, ombros e peito, em sequência. | Aplicar redução/absorção de dano a partir do momento em que cada peça fecha, conforme regra definida no servidor. |
| 2,00–2,50 s | Ombros travam; poeira e lascas caem. | Armadura completa, com silhueta legível e juntas móveis. |
| 2,50–10,50 s | Pose de guarda sutil, com compensação de peso; ao receber golpe, a placa correspondente trinca, solta lascas ou quebra. | Até 8 s de proteção: redução de 35% do dano, redução de 50% do knockback, velocidade -10%, absorção máxima de 24 HP. Não acumula com outro escudo defensivo. |
| 10,50–11,50 s | Placas desprendem em direção ao chão; braços descruzam e peso volta ao normal. | Encerrar estado, devolver velocidade e parar a drenagem. Se a absorção chegar a 24 HP antes, quebrar visual e mecanicamente naquele momento. |

**Energia:** ficha antiga é inconsistente (custo de 28 em um trecho, mas 22 + 3/s em outro). Referência de implementação: 22 de ativação + 3 de manutenção por segundo, duração máxima 8 s; não manter proteção sem energia. Valores finais devem ser calibrados no servidor.  
**Aceite:** armadura acompanha o jogador andando, girando, pulando e nadando; dano reduzido medido; knockback reduzido medido; velocidade limitada; placas reagem a golpes; termina sem deixar placas flutuando.

### 3.6 Terremoto — técnica suprema com cicatrizes permanentes

**Papel:** controle de grande área, alteração real e limitada do terreno e dano em ondas. É maior, mais demorado e mais destrutivo que Tremor Sísmico.  
**Duração da execução visual:** aproximadamente 12 s. **Cicatrizes:** permanentes no mundo; não são removidas ao fim da animação.

| Tempo | Corpo e leitura visual | Física / gameplay |
|---|---|---|
| 0,00–1,50 s | Jogador agacha profundamente, abre a base e coloca as duas mãos no chão. O tronco permanece firme; sem gestos rápidos de pulso. | Canalização. Definir epicentro, raio de 12 blocos e conjunto determinístico de linhas de ruptura. |
| 1,50–3,00 s | Rachaduras se propagam em linhas ramificadas, pedras pequenas começam a vibrar/levitar alguns centímetros. | O chão comunica a tensão acumulada. A frente de fissura cresce ao longo da superfície, não em círculos decorativos presos ao jogador. |
| 3,00–3,80 s | Jogador ergue os braços com esforço; o corpo sobe do agachamento e o peso é transferido para trás. | Preparar o impulso principal. |
| 3,80–4,70 s | Primeira onda: tremor de baixa amplitude percorre o raio; poeira e cascalho saltam. | Primeiro pulso de dano/instabilidade, uma vez por alvo. |
| 4,70–5,60 s | Segunda onda: fissuras alargam, placas se deslocam e algumas bordas cedem. | Segundo pulso limitado; aplicar deslocamento/desequilíbrio com intensidade controlada. |
| 5,60–6,50 s | Terceira onda: cristas e placas de terreno se levantam e caem; a poeira é empurrada para fora. | Terceiro e último pulso; máximo de 3 impactos por alvo, com limite total de dano definido. |
| 6,50–9,00 s | Fragmentos caem, placas assentam, poeira atravessa o local e o jogador permanece firme antes de recuperar. | A deformação visível permanece. As linhas de fratura não desaparecem aos 96 ticks. |
| 9,00–12,00 s | Recuperação corporal gradual e fim do tremor residual. | Encerrar VFX e estado de combate; cicatrizes continuam no terreno. |

**Fraturas permanentes:** gerar um conjunto limitado e determinístico de fendas irregulares radiais/ramificadas no terreno, com profundidade e largura controladas; a superfície deve ficar realmente marcada (blocos removidos/alterados ou peças de fissura persistentes), não apenas receber ribbons client-side. Não restaurar por timer.  
**Limites de segurança:** não tocar em block entities/containers, bedrock, líquidos, blocos não naturais/fora da lista permitida ou chunks indisponíveis. A área alterada deve ter limite máximo de blocos por uso; os blocos afetados devem ser registrados no log de teste. Não propagar por todo o mundo.  
**Balanceamento de referência:** raio 12; três ondas; até 4 HP por onda (máximo 12 HP por alvo); energia e recarga maiores que Tremor Sísmico.  
**Aceite:** as três ondas são visíveis e chegam em tempos distintos; o dano acompanha cada onda e não duplica; as cicatrizes são visíveis depois de 1 minuto e depois de salvar/recarregar o mundo; o log lista cada bloco alterado e nenhum container/BlockEntity foi tocado.

## 4. O que a implementação atual faz de errado e precisa ser removido/corrigido

- `PlayerAbilityAnimator.DURATION_TICKS = 38`: todas as poses terminam em cerca de 1,9 s, mesmo quando uma técnica precisa de carga, impacto e recuperação mais longos.
- `ElementalAbilityService.scheduleTechnique`: reutiliza uma linha do tempo de 34 ticks para técnicas com funções distintas; não representa as durações especificadas acima.
- `applyCombat`: aplica dano genérico na ativação por uma AABB ampla. Isso faz técnicas defensivas/construtivas (muralha e armadura) agirem como ataques; não é colisão física.
- `rock_throw`: o caminho visual é calculado por fórmulas locais e não tem projétil autoritativo com velocidade, gravidade e colisão no servidor.
- `rock_cage`: o centro atual é calculado à frente do conjurador; não é ancorado a um alvo real.
- `stone_wall`: o mesh retrai perto do final, enquanto os blocos do servidor usam duração de 60 ticks; ambos contradizem a decisão de permanência.
- `stone_armor`: a geometria usa origem/direção capturadas na ativação e fica ancorada no mundo; não acompanha os ossos/partes do jogador. Também não deve causar dano radial.
- `seismic_wave` e `earthquake`: a geometria de rachaduras é apenas renderização transitória; os blocos temporários atuais são restaurados em 40 ticks. Isso não cumpre a cicatriz permanente solicitada.
- `TechniqueVisualRenderer.durationTicksFor` e a animação corporal usam relógios diferentes; precisam ser unificados por estado de habilidade.
- Os valores de `ElementalAbility` não correspondem consistentemente às fichas originais e devem ser revisados junto com o comportamento, não isoladamente.

## 5. Estratégia de arquitetura exigida

- Criar uma execução de habilidade com id, caster, alvo/epicentro, tick inicial, fases e parâmetros; evitar listas de `Runnable` que apenas despejam efeitos em tempos fixos.
- Implementar projétil de pedra autoritativo (entidade física ou estado de projétil no servidor com colisão varrida) e sincronizar posição/rotação para renderizar o mesmo objeto no cliente.
- Implementar construção persistente de muralha/prisão como blocos/estruturas reais, com validação de posição e sem restauração automática.
- Implementar o Terremoto como alteração persistente de mundo com registro/limite de blocos alterados; não usar mesh transitório como substituto.
- Implementar Armadura de Pedra como estado defensivo no servidor e placas renderizadas em coordenadas locais do modelo do jogador, com dano/knockback/velocidade medidos.
- Criar linha temporal por habilidade para corpo e VFX. Cada fase visual deve corresponder a um evento mecânico ou explicar uma preparação/recuperação.
- Não colocar toda a lógica numa classe monolítica. Separar timeline, física de projétil, estruturas persistentes, fraturas e estado defensivo.

## 6. Matriz de testes e aprovação

| Técnica | Teste obrigatório | Prova de aprovação |
|---|---|---|
| Rochas | Alvo, parede, chão, três projéteis e latência | Vídeo + log: trajetória/colisão/dano por projétil, sem atravessar bloco |
| Muralha | Colisão, projétil, linha de visão, 60 s e reload | Parede real permanece; blocos listados; sem sobrescrita de container |
| Prisão | Alvo móvel, alvo grande, jogador, quebra da gaiola | Gaiola acompanha alvo na formação; alvo contido; estrutura persiste até ser quebrada |
| Tremor | Alvos no solo/ar e distância crescente | Frente chega em sequência; dano/knockback ocorre no cruzamento; um hit por alvo |
| Armadura | Medir dano antes/depois, knockback, velocidade e expiração | 35%/50%/-10%/24 HP/8 s medidos; placas acompanham o modelo |
| Terremoto | Três pulsos, limites, blocos protegidos e reload | Três impactos distintos; cicatrizes permanentes; lista de blocos alterados; nenhum BlockEntity tocado |

**Não aprovar:** build verde sem teste visual, marker de log sem imagem, partícula sem geometria, mesh sem física, efeito persistente apenas enquanto o jogador permanece online, animação corporal que termina antes da ação, bloco colocado em posição inválida ou estrutura que some automaticamente.

## 7. Referências de estudo

- Netflix Tudum — “What Is Bending? The Avatar: The Last Airbender Cast Explains”: https://www.netflix.com/tudum/articles/what-is-bending-avatar-last-airbender
- Avatar Wiki — Earthbending: https://avatar.fandom.com/wiki/Earthbending
- Avatar Wiki — Earthbending training / “Bitter Work”: https://avatar.fandom.com/wiki/Earthbending_training
- Avatar Wiki — transcript “Bitter Work”: https://avatar.fandom.com/wiki/Transcript:Bitter_Work

As fontes servem como referência de linguagem visual, coreografia e possibilidades de dobra; o mod deve manter implementação e identidade próprias.
