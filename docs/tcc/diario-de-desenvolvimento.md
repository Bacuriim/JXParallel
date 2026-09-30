# Diário de desenvolvimento do JXParallel

Registro cronológico das estratégias, decisões e métricas do projeto, mantido como fonte para
o TCC em Engenharia de Computação (IFCE). Cada entrada diz o que foi tentado, por quê, o que as
medições mostraram e o que mudou depois. Números sempre com data, ambiente e link para os dados
brutos em `docs/`.

**Objetivo do projeto** (definido pelo autor em 2026-09-24): reproduzir o que o JavaFX oferece,
fazer melhor e ir além, com foco em desempenho para aplicações grandes. Público-alvo: Java 8 até
a versão mais recente, em 32 e 64 bits.

## Metodologia de medição (estado atual)

- Cada implementação roda em uma JVM nova por execução, alternando JavaFX e JXParallel para
  que variações da máquina afetem os dois lados.
- 5 execuções por lado; o relatório usa a mediana e mostra a faixa entre execuções.
- Memória do processo (working set, bytes privados, handles) amostrada fora da JVM a cada
  10 ms com `Get-Process`. Heap, non-heap e memória direta amostrados dentro da JVM a cada 10 ms.
- CPU de processo via `OperatingSystemMXBean.getProcessCpuTime` (granularidade de 15.6 ms no
  Windows). Alocação via `ThreadMXBean.getThreadAllocatedBytes` das threads vivas.
- Os dois lados executam exatamente os mesmos cenários e as mudanças precisam chegar à tela
  (ver entrada 2026-09-24, "Validade do teste de UI").
- A carga de outros processos é registrada antes e depois de cada bateria. Baterias com a
  máquina ocupada são descartadas ou marcadas.

Máquina de referência: Windows 11, Intel Core i7-1255U (12 threads), Intel Iris Xe, tela a
cerca de 102 Hz. JDK 17.0.12 x64 e OpenJFX 21.0.2, salvo indicação.

---

## 2026-09-22: primeira versão (desenvolvida com o Google Antigravity)

**Estratégia.** Biblioteca em módulos Maven: `jxparallel-core` (pool de workers adaptativo com
fila limitada, prioridades, cancelamento, métricas), ponte com o JavaFX (`jxparallel-javafx`,
`jxparallel-fxml` com cache de FXML) e um modelo de UI próprio, independente do JavaFX
(`jxparallel-ui`), renderizado primeiro com Java2D.
Commits `1765c72` a `4652c57`.

**Sequência de motores gráficos no mesmo dia.**
1. Java2D (AWT), commit `1765c72`.
2. Skia via Skija, desenhando num bitmap e copiando para um `BufferedImage` do AWT (`d331296`).
3. Backend OpenGL opcional com LWJGL, primitivas cruas sem Skia (`1ba1d5e`).
4. Backend Vulkan com LWJGL (`7f601d7`), promovido a motor principal (`8453c0e`).

**Primeiras métricas** (JDK 21 x64, [ui-performance-2026-09-22.md](../ui-performance-2026-09-22.md)):

| Métrica | JavaFX | JXParallel (Skia) |
|---|---:|---:|
| Início até o primeiro quadro | 312 ms | 397 ms (+27%) |
| CPU de processo | 750 ms | 469 ms |
| Delta de heap | 7.2 MB | 10.2 MB |

Java 8 x86 ([metrics-java8-x86-report.md](../metrics-java8-x86-report.md)): início +11.3%,
clique até o resultado +7.0%, delta de heap -45.8%, CPU +15.2% para o JXParallel.

**Teste de estresse de UI** (`docs/ui-stress-load-report.md`, removido do repositório em 2026-09-25;
recuperável com `git show 4652c57:docs/ui-stress-load-report.md`): o relatório
indicava o JXParallel 40% mais rápido no total e 74.6% mais rápido para atualizar um label.
Esses números foram invalidados em 2026-09-24 (ver abaixo).

**Problema encontrado.** O Vulkan derrubava a JVM com `EXCEPTION_ACCESS_VIOLATION` no driver da
Intel (`igvk64.dll`). Causas levantadas: dependência de subpass sem `srcAccessMask` e swapchain
não recriado em `VK_ERROR_OUT_OF_DATE_KHR`.

---

## 2026-09-24: abandono do Vulkan; Skia sobre GLFW/OpenGL

**Decisão.** Abandonar o Vulkan e usar Skia (Skija) desenhando direto no framebuffer de uma
janela GLFW com contexto OpenGL, via `DirectContext` da GPU. Eliminou a cópia pixel a pixel
para `BufferedImage` e a dependência do AWT na janela. Commit `116b1fe`.

**Motivação.** O Vulkan exigia muito código (910 linhas só na janela) e ainda não desenhava
texto; o crash na Intel bloqueava o uso real. O caminho Skia + OpenGL já existia em partes.

**Resultado.** A janela abre e desenha texto e controles sem crash. Restrição descoberta: o Skija
não publica bibliotecas nativas de 32 bits, então a janela só roda em JVM 64-bit.

---

## 2026-09-24: validade do teste de UI

**Problema metodológico.** No `NativeStressRunner`, `JXLabel.setText()` seguido de
`window.requestRender()` não mudava nada na tela: a árvore nativa é um instantâneo criado em
`setContent(content.render())`. O teste media só o custo de um setter, enquanto o lado JavaFX
pagava invalidação de CSS e layout. Isso favorecia o JXParallel.

**Correção.** Cada atualização passou a chamar `setContent(content.render())`, que é o custo
real para a mudança chegar à tela. Foi adicionada uma fase sustentada: 600 quadros, cada um
atualizando todos os componentes, com ritmo definido pelo vsync, medindo intervalo entre quadros
(p50, p95, p99, pior quadro), quadros acima de 25 ms, CPU e alocação. Commit `a955d1c`.

**Métricas após a correção** (mediana de 5, [ui-stress-comparison-2026-09-24.md](../ui-stress-comparison-2026-09-24.md)):

| Métrica | JavaFX | JXParallel | Diferença |
|---|---:|---:|---:|
| Pico de RAM (working set) | 248.6 MB | 159.8 MB | -36% |
| Heap vivo após GC | 10.6 MB | 2.2 MB | -80% |
| CPU total do processo | 10.0 s | 3.1 s | -69% |
| CPU por quadro | 10.6 ms | 3.0 ms | -72% |
| Pior quadro | 53.2 ms | 21.4 ms | -60% |
| Atualizar label (500x) | 11.6 ms | 30.0 ms | **+160%** |

**Leitura.** Com o teste corrigido, o label passou de "74.6% mais rápido" para "160% mais lento":
reconstruir a árvore inteira a cada mudança custa mais que a invalidação pontual do JavaFX.
Parte da vantagem em memória e CPU vem de o JXParallel desenhar menos (sem CSS, sem skins).

**Reexecução em 2026-09-25** com a máquina ociosa: a apresentação de quadros ficou limitada
(cerca de 30 e 25 FPS nos dois lados), invalidando as métricas de quadro. Memória (-34%), rajada
(-77%) e CPU por quadro (-47%) confirmaram a direção.

*Correção de diagnóstico.* Atribuí a limitação à sessão bloqueada porque o processo `LogonUI`
estava ativo. Depois se verificou que o `LogonUI` pertencia à sessão desconectada de outro usuário
da mesma máquina, e que a área de trabalho estava visível. A causa mais provável foi o monitor
desligado por inatividade enquanto o autor acompanhava remotamente. Lição: verificar se os
quadros estão sendo apresentados (FPS próximo da taxa do monitor) antes de aceitar uma bateria,
em vez de inferir o estado da tela por processos.

---

## 2026-09-24: suporte a 32 e 64 bits, Java 8 até a versão atual

**Levantamento** (Maven Central, LWJGL 3.3.3): há bibliotecas nativas `windows-x86` para
`lwjgl`, `glfw`, `opengl`, `nanovg`, `stb`, `freetype`, `harfbuzz` e `yoga`. O Skija não tem
(`skija-windows-x86` retorna 404).

**Restrição da plataforma.** O port Windows x86 foi descontinuado no JDK 21 (JEP 449) e removido
no 24 (JEP 479). *Correção em 2026-09-25:* a primeira execução da CI mostrou que nenhuma das
distribuições usadas (Temurin, Zulu) publica JDK 21 de 32 bits para Windows; o último é o 17.
A matriz real é: x86 de 8 a 17, x64 de 8 até a mais recente.

**Decisão proposta (pendente).** Trocar o Skia por NanoVG para formas, HarfBuzz + FreeType para
texto e Yoga para layout, todos via LWJGL, cobrindo a matriz inteira com um único renderer.
Alternativa descartada: Skia em 64 bits e NanoVG em 32 bits, por exigir dois renderers e gerar
diferenças visuais.

**Cuidados de build identificados.** Compilar com `--release 8` (compilar num JDK 9+ só com
`source`/`target` 1.8 gera chamadas como `ByteBuffer.flip()` com assinatura nova, que quebram no
Java 8). No JDK 24+, carregar código nativo emite aviso (JEP 472); usar
`--enable-native-access=ALL-UNNAMED`.

---

## 2026-09-24: prioridades arquiteturais para aplicações grandes

Com o objetivo definido, o gargalo deixou de ser o motor gráfico e passou a ser a arquitetura:

1. Árvore retida com atualização incremental (hoje a árvore inteira é reconstruída a cada mudança).
2. Redesenho só das regiões alteradas e cache de camadas.
3. Listas, tabelas e árvores virtualizadas desde o início.
4. Layout e medição de texto fora da thread de UI.
5. Qualidade de texto igual ou superior à do JavaFX (shaping e rasterização).

---

## 2026-09-24 e 25: carregamento de FXML com controllers

**Estratégia.** Comparar o uso típico do JavaFX (`FXMLLoader.load` sequencial na thread do FX)
com `FXMLLoaderService.loadAsync` do JXParallel (pool de workers e cache de FXML). 20 telas
geradas (formulário com barra de ferramentas, 3 abas com 15 campos cada, cerca de 135 elementos,
controller com 21 campos `@FXML` e `initialize()`). Três passadas por JVM: a primeira fria,
a terceira quente. Cada passada confere que os 20 controllers executaram. Commit `a955d1c`.

**Bugs encontrados no core durante o teste** (commit `9dc258c`):
1. O `AdaptiveWorkerPool` nunca passava de `threads.min` (2 threads). O `ThreadPoolExecutor` só
   cria threads acima do core quando a fila recusa uma tarefa, e a fila tinha milhares de vagas.
   A opção `threads.auto-scale` era lida e ignorada. Correção: com auto-scale, core igual a
   `threads.max` e threads ociosas expiram após o keep-alive.
2. A fila limitada não devolvia a permissão em `poll(timeout)`, método usado por threads que
   podem expirar. As permissões vazavam até a fila parecer cheia e rejeitar tarefas.
   Um teste (`AdaptiveWorkerPoolScalingTest`) falha sem a correção e passa com ela.

**Escalabilidade por número de workers** (passada quente, mediana de 3):
1 thread 1221 ms, 2 threads 1170 ms, 4 threads 619 ms, 8 threads 442 ms.

**Resultado com a máquina ociosa** (2026-09-25, mediana de 5, [fxml-load-comparison.md](../fxml-load-comparison.md)):

| Métrica | JavaFX | JXParallel | Diferença |
|---|---:|---:|---:|
| 20 telas, fria | 1163 ms | 725 ms | 1.6x mais rápido |
| 20 telas, quente | 665 ms | 194 ms | 3.4x mais rápido |
| Thread de UI bloqueada (fria) | 1163 ms | 0 ms | |
| Primeira tela pronta, quente | 30 ms | 72 ms | 2.4x mais lento |
| Pico de RAM | 319 MB | 395 MB | +24% |
| Alocação por passada quente | 213 MB | 215 MB | igual |

**Achados.**
- O cache de FXML guardava só os bytes do arquivo e não trouxe ganho: o custo está no parse do
  XML, na reflexão para criar nós e na injeção `@FXML`, não na leitura do arquivo.
- Uma bateria feita com 27 a 42% da CPU ocupada por outros processos mostrou "+54% de CPU" no
  JXParallel. Com a máquina ociosa, a CPU empatou (1.98 s contra 1.86 s). A bateria ruidosa foi
  descartada. Lição: controlar a carga da máquina é obrigatório.

**Estratégia "primeira tela primeiro"** (2026-09-25). A prioridade de tarefas não resolve a
primeira tela lenta: com 8 workers ela já começa imediatamente, mas disputa CPU com outras 7
telas. Nova estratégia: carregar sozinha a tela que o usuário abriu e só depois disparar as
demais em paralelo.

| Métrica (mediana de 5, máquina com 27 a 33% de carga) | JavaFX | JXParallel |
|---|---:|---:|
| Primeira tela, quente | 64 ms | 59 ms |
| Primeira tela, fria | 512 ms | 468 ms |
| 20 telas, quente | 1198 ms | 324 ms (3.7x) |
| 20 telas, fria | 1931 ms | 972 ms (2.0x) |

A primeira tela deixou de ser um ponto fraco e o ganho no lote se manteve.

---

## 2026-09-25: correções rápidas e limpeza

**Build compatível com Java 8 em qualquer JDK.** Profile `release-8`, ativado em JDK 9+, que usa
`maven.compiler.release=8`. Sem ele, compilar num JDK novo com `source`/`target` 1.8 liga o código
à API nova (por exemplo `ByteBuffer.flip()` retornando `ByteBuffer`) e quebra no Java 8.

**Layout.** `column` e `row` dividiam o espaço igualmente entre os filhos: numa janela com um
texto e um botão, o botão ocupava metade da altura. Agora seguem o modelo do `VBox`/`HBox` do
JavaFX: tamanho preferido no eixo principal e preenchimento no eixo cruzado. Teste novo
`columnShouldGiveChildrenTheirPreferredHeightAndFullWidth`.

**Concorrência no `JXObservableList`.** Depois de mover os listeners para fora do lock, `clear()`
lia `size()` e removia aquele índice em passos separados: uma remoção concorrente no meio gerava
`IndexOutOfBoundsException`. `clear()` e `addAll()` voltaram a ser atômicos (mutação inteira sob o
lock, eventos disparados depois). O teste `clearShouldNotRaceWithConcurrentRemovals` falha no
código anterior e passa no novo.

**AWT removido do `jxparallel-ui`.** `JXNativeNode` usa `int` no lugar de `Rectangle`/`Dimension`;
`JXStyle`/`JXTheme` guardam cores como ARGB `int` (formato consumido pelo renderer); o clipboard
do `JXTextDocument` passou a ser a interface `JXClipboard`, com implementação via GLFW no
`JXWindow`. O módulo de UI não importa mais nada de `java.awt` nem `javax.swing`.

**Removidos.** Módulo `jxparallel-lwjgl` (OpenGL cru, sem Skia, redundante depois da troca) e o
relatório de estresse de 2026-09-22 com os números inválidos. O README passou a mostrar os
resultados atuais e a registrar por que o relatório antigo foi retirado.

**Integração contínua.** Matriz ampliada para cobrir o público-alvo: Windows x86 com Java 8 e 21,
Windows x64 com Java 25, além dos jobs existentes. O Java 8 compila só os módulos de runtime
(`core` e `ui`), porque o Mockito 5 exige Java 11.

**Validação.** JDK 8 x86: 36 testes (core e UI). JDK 17 x64: 48 testes em todos os módulos,
incluindo JavaFX e FXML. Nenhuma falha.

---

## 2026-09-25: dois renderers, Skia em 64 bits e NanoVG em 32 bits

**Decisão do autor.** Manter o Skia nas JVMs de 64 bits e usar NanoVG só nas de 32 bits. Motivo:
depois do Java 8 quase ninguém usa 32 bits, e o port Windows x86 do JDK termina no 21. Assim o
caminho principal mantém a qualidade de texto do Skia e o Java 8 x86 passa a ter janela nativa.
Isso substitui a proposta anterior (NanoVG para todos).

**Implementação.** `JXWindow` escolhe o backend em tempo de execução por
`sun.arch.data.model` (32 → NanoVG, senão Skia), com `-Djx.renderer=skia|nanovg` para forçar um
deles. Os backends são classes internas; a do Skia só é carregada em 64 bits, então uma JVM de 32
bits nunca toca no Skija. `JXNanoVGRenderer` desenha as mesmas formas, cores e tamanhos do
`JXSkiaRenderer`, usando NanoVG com contexto OpenGL 3 e uma fonte TrueType do sistema (Segoe UI no
Windows; `-Djx.font` para outra). O pom ganhou um profile de natives Windows x86 só com LWJGL e
NanoVG. Teste novo: `JXWindowRendererTest`.

**Primeira execução em 32 bits.** Pela primeira vez a janela nativa abriu no Java 8 32-bit.
Execução curta (50 atualizações por componente, 60 quadros, sessão bloqueada):

| JVM | Motor | Quadros | Pico de heap |
|---|---|---:|---:|
| Java 8 x86 | NanoVG (automático) | 60 | 5.4 MB |
| Java 17 x64 | Skia (automático) | 60 | 12.0 MB |
| Java 17 x64 | NanoVG (forçado) | 60 | 10.0 MB |

Primeiro contato com JavaFX 8 contra JXParallel/NanoVG, ambos em Java 8 x86 (1 execução, valores
indicativos): pico de heap 7.8 contra 5.5 MB, heap vivo após GC 5.8 contra 2.3 MB, classes
carregadas 2808 contra 1236, primeiro quadro 1936 contra 1053 ms de uptime.

**Verificação visual.** As duas janelas foram capturadas lado a lado
([renderers-nanovg-vs-skia.png](../assets/renderers-nanovg-vs-skia.png)): mesmo layout e cores; o
texto do NanoVG sai um pouco maior (15 px contra 13 px no Skia).

**Baterias completas** (mediana de 5, tela ativa a cerca de 118 Hz, 5 a 23% de carga de fundo,
[ui-comparison-2026-09-25.md](../ui-comparison-2026-09-25.md)):

| Métrica | JavaFX 8 x86 | JXParallel NanoVG x86 | JavaFX 21 x64 | JXParallel Skia x64 |
|---|---:|---:|---:|---:|
| Primeiro quadro (uptime) | 777 ms | 609 ms | 1494 ms | 840 ms |
| Pico de working set | 97.3 MB | 89.3 MB | 247.5 MB | 160.1 MB |
| Heap vivo após GC | 7.6 MB | 2.1 MB | 10.6 MB | 2.1 MB |
| CPU por quadro | 3.9 ms | 1.5 ms | 11.3 ms | 2.9 ms |
| Alocação em 600 quadros | 24.5 MB | 3.9 MB | 34.1 MB | 5.0 MB |
| FPS | 118.2 | 118.5 | 117.3 | 118.7 |
| Pior quadro | 30.4 ms | 40.2 ms | 55.0 ms | 39.1 ms |
| Atualizar label 500x | 13.8 ms | 30.8 ms | 14.7 ms | 17.6 ms |

**Leitura.** Em 32 bits a diferença de working set é pequena (-8%), porque o JavaFX 8 na client VM
já é enxuto; o ganho aparece em bytes privados (-34%), heap e alocação. A CPU por quadro é 2.5 a 4
vezes menor nas duas plataformas, com o mesmo FPS. O label continua mais lento nos dois casos.
O pior quadro do NanoVG (40 ms, um único quadro) precisa de mais execuções para virar conclusão.

**Ferramenta nova.** `-Djx.monitor=N` abre a janela no monitor N, no `JXWindow` e no runner do
JavaFX, e `-Monitor` no script. A bateria x64 rodou no segundo monitor para não atrapalhar o uso
da máquina.

---

## 2026-09-25: atualização incremental da árvore de UI

**Diagnóstico antes de mudar.** Um microbenchmark separou o custo de cada atualização em render
(criar a árvore de `JXElement`), montagem (criar a árvore nativa) e layout. Com os 5 nós do teste,
os três juntos custavam só 6 µs por atualização, contra cerca de 60 µs medidos no teste de
estresse. O custo que faltava vinha de `requestRender()`, que chamava `glfwPostEmptyEvent()` (uma
chamada ao sistema) a cada atualização. Em telas grandes o problema era outro: com 2005 nós, cada
atualização custava 0.25 ms e crescia linearmente com o tamanho da tela.

**Primeira tentativa, insuficiente.** Reconciliação sozinha (`JXNativeNode.reconcile`: reaproveitar
nós do mesmo tipo e só trocar as props) não reduziu o custo com 2005 nós (127 ms contra 122 ms em
500 atualizações), porque o `render()` ainda recriava todos os elementos e a reconciliação
precisava percorrer todos.

**Solução.** Três partes:
1. Pedidos de redesenho agrupados com `AtomicBoolean`: só o primeiro pedido após um quadro acorda
   o laço de renderização.
2. Memoização por controle (`JXRenderMemo`): `render()` devolve a mesma instância enquanto o estado
   lido na hora não muda. A chave é o estado atual, não listeners, então não há como a tela ficar
   desatualizada.
3. Atalho por identidade na reconciliação: se o elemento é a mesma instância do render anterior, a
   subárvore inteira é pulada. O layout só é invalidado quando a estrutura muda. Efeito colateral
   positivo: o nó com foco deixa de ser perdido a cada atualização.

**Microbenchmark** (500 atualizações de label, render + árvore nativa + layout, Java 17):

| Tamanho da tela | Antes | Depois |
|---|---:|---:|
| 5 nós | 5.6 ms | 1.6 ms |
| 205 nós | 17.8 ms | 4.4 ms |
| 2005 nós | 127.4 ms | 30.9 ms |

**Achado metodológico.** A fase de label é a primeira do teste de estresse e roda com o JIT frio. O
botão, que faz mais trabalho e vem depois, era mais rápido que o label. Os dois runners passaram a
repetir a fase de label no fim (`stress_label_warm_ns`), de forma simétrica, sem alterar as métricas
existentes.

**Resultado no teste de estresse** (x86: 10 execuções; x64: 5; carga de fundo 10 a 37%):

| Métrica | JavaFX 8 x86 | JXParallel x86 | JavaFX 21 x64 | JXParallel x64 |
|---|---:|---:|---:|---:|
| Label 500x, JIT frio | 11.4 ms | 10.3 ms | 10.3 ms | 10.7 ms |
| Label 500x, quente | 2.57 ms | 0.93 ms | 3.34 ms | 2.59 ms |
| Rajada total | 147.0 ms | 25.2 ms | 234.8 ms | 33.2 ms |
| Pior quadro | 31.1 ms | 23.6 ms | 56.9 ms | 13.1 ms |
| Quadros acima de 25 ms | 1 | 0 | 2 | 0 |

O label deixou de ser ponto fraco: empate com JIT frio e vantagem com JIT quente.

**Pior quadro do NanoVG.** Com 10 execuções, o pior quadro do NanoVG ficou entre 21.1 e 25.2 ms em
todas, com zero quadros acima de 25 ms em 9 delas. Os 40.2 ms da bateria anterior foram um caso
isolado. Não houve mudança de código específica para isso.

---

## 2026-09-25: cache de FXML com template pré-interpretado

**Problema.** O cache antigo guardava os bytes do arquivo, e as passadas quentes alocavam o mesmo
que o JavaFX (cerca de 223 MB para 20 telas). O custo estava no parse do XML, nas buscas por
reflexão e na maquinaria do `FXMLLoader` (builders, expressões), repetidos a cada carga.

**Estratégia.** `FxmlTemplate`: na primeira carga, o FXML é lido com DOM e vira um plano imutável,
com classes resolvidas, construtor escolhido (inclusive `@NamedArg`, preferindo `int` a `double`
quando o valor permite), setters, propriedades estáticas (`GridPane.rowIndex`), propriedade
padrão (`@DefaultProperty`), campos `@FXML` do controller e métodos de evento. Os valores dos
atributos já são convertidos nessa etapa. As cargas seguintes só chamam construtores e setters.
O que o template não implementa (`fx:include`, `fx:define`, expressões, `%recursos`,
`@locais`, scripts, texto dentro de elementos) cai no `FXMLLoader`, então nunca fica pior que antes.

**Bug encontrado pela validação.** Na primeira execução, o template parecia 20x mais rápido, mas
nenhuma tela passava na checagem de controller inicializado. O `FXMLLoader` também copia o `fx:id`
para o `id` do nó (é isso que faz `lookup("#status")` funcionar), e o template não fazia isso.
Depois da correção, foi acrescentada ao benchmark uma impressão digital estrutural: contagem de
todos os nós alcançáveis e somas dos valores dos `Spinner` e dos índices de linha do `GridPane`.
Os três modos deram 2300 nós, 4320 e 12600 em todas as passadas. Lição: um ganho grande demais
pede uma checagem de equivalência, não só de "rodou sem erro".

**Resultado** (mediana de 5; terceiro modo isola o template: mesmo pool com `FXMLLoader` dentro):

| Métrica | JavaFX sequencial | Pool + `FXMLLoader` | Pool + template |
|---|---:|---:|---:|
| 20 telas, quente | 692 ms | 258 ms | 31 ms |
| 20 telas, frio | 1467 ms | 818 ms | 538 ms |
| Primeira tela, quente | 40.7 ms | 36.3 ms | 7.8 ms |
| CPU, passada quente | 2.28 s | 1.95 s | 0.30 s |
| Alocação, passada quente | 223.5 MB | 225.0 MB | 9.8 MB |
| Alocação, passada fria | 245.4 MB | 248.2 MB | 318.7 MB |
| Pico de working set | 330 MB | 412 MB | 322 MB |

A tela individual passou a ser 5x mais rápida que no JavaFX (antes só o lote ganhava), e o pico de
memória deixou de ficar acima do JavaFX. Custo: a primeira carga de cada arquivo aloca mais, e os
templates em cache retêm cerca de 1.5 MB a mais de heap.

## 2026-09-25: estratégia de testes em cinco camadas, comparada com o OpenJFX

**Pergunta.** Dá para copiar os testes do JavaFX trocando os componentes? Não: o OpenJFX é GPLv2
com Classpath Exception (copiar o código tornaria esses arquivos GPL num projeto MIT) e os testes
dependem de internos do JavaFX (skins, CSS, pulse, `com.sun.*`). A estratégia foi reimplementada.

**Como o OpenJFX testa** (repositório `openjdk/jfx`, branch `master`, JavaFX 28 em
desenvolvimento, lido em 25/09 via Sourcegraph, `build.gradle`, `verification-metadata.xml` e
`submit.yml`): 1.192 arquivos de teste unitário nos módulos, rodando sem tela com o `StubToolkit`
e 182 classes "shim" injetadas por `--patch-module`; 499 arquivos de teste de sistema, 174 com
`Robot` e checagem de cor em pontos escolhidos, só com `-PFULL_TEST=true -PUSE_ROBOT=true`;
257 arquivos de teste manual; 11 arquivos de apps de desempenho, sem JMH; 28 arquivos com teste de
vazamento no padrão JMemoryBuddy. A única biblioteca de teste declarada é o JUnit 6.1.3. Não há
jqwik, jcstress, JMH, ArchUnit nem JaCoCo; cobertura só com JCov em builds fechados da Oracle.
Propriedades e coleções do JavaFX não são thread-safe (só a FX thread), então não há contrato de
concorrência para testar.

**Estratégia.** Cinco camadas usadas em bibliotecas grandes (Guava, Caffeine, Netty, o próprio
JDK), cada uma validada injetando um bug de propósito e vendo o teste falhar:

1. **Propriedades (jqwik).** Reconcile de árvore aleatória contra montagem do zero, e o
   `FxmlTemplate` contra o `FXMLLoader` em FXML aleatório (teste diferencial: o `FXMLLoader` é a
   especificação). Um bug injetado (ignorar mudança de `gap`) passou por 500 pares de árvores
   independentes e só foi pego pela propriedade de "pequenas edições", que o jqwik reduziu ao caso
   mínimo em 19 passos. Lição: o gerador precisa imitar o uso real.
2. **Concorrência (jcstress, ferramenta do OpenJDK).** Cinco testes, dezenas de milhões de
   execuções cada.
3. **Snapshot e interação.** Layout em texto, imagem golden do Skia renderizada em memória (sem
   GPU) e um teste de clique ponta a ponta sem janela. Mudar o azul do botão em 9 níveis gera
   11.911 pixels diferentes e falha o teste.
4. **Regressão de desempenho (JMH + gate na CI).** O gate compara razões entre benchmarks da mesma
   execução, que não dependem da velocidade da máquina. Tirar o atalho de identidade do reconcile
   derrubou as razões de 57x e 23.978x para 6,0x e 4,7x e o gate falhou.
5. **Contrato.** ArchUnit (sem AWT/Swing/JavaFX, core sem renderer, só `native2d` fala com
   GLFW/Skia/NanoVG, sem ciclos), vazamento de memória, JaCoCo e PIT.

**Bugs encontrados** (todos corrigidos, todos com teste):

| Camada | Bug | Evidência |
|---|---|---|
| 1 | `FxmlTemplate` copiava `fx:id` para qualquer `id`; o `FXMLLoader` só copia com `@IDProperty` | caso mínimo: `<Box fx:id="a">` aninhado |
| 1 | `FxmlTemplate` aceitava nomes de classe aninhada que o `FXMLLoader` rejeita | FXML feito para o JX não abria no JavaFX puro |
| 2 | `JXObservableList` entregava eventos fora de ordem | 28.027 em 30,4 milhões |
| 2 | `JXProperty` notificava duas vezes a mesma mudança | 2.956.817 em 46,8 milhões (6,3%) |
| 2 | `JXProperty` e `JXState`: último valor visto pelo listener diferente do valor atual | 66.544 e 9.926 |
| 3 | Nenhum botão da UI nativa respondia a clique (`onAction` gravado, `onClick` lido) | teste de clique |
| 3 | Botão desabilitado receberia clique (o JavaFX não entrega) | teste de clique |
| 5 | `JXProperty` ligado com `bind` e esquecido nunca era coletado (o JavaFX usa referência fraca) | teste de vazamento |

**Correção de concorrência e custo.** Mudança e notificação passaram a acontecer sob o mesmo lock
(na lista, um lock de escrita separado para `get`/`size` não esperarem listeners lentos), e as
listas de listeners viraram `CopyOnWriteArrayList`. Depois: zero estados proibidos em cerca de
419 milhões de amostras. Custo medido com JMH, thread única, código antigo e novo em seguida:

| Operação com um listener | Antes | Depois |
|---|---:|---:|
| `JXObservableList` add + remove | 105,9 ns | 64,9 ns |
| `JXProperty.set` | 37,8 ns | 28,8 ns |
| `JXState.set` | 31,6 ns | 24,2 ns |

Ficou thread-safe e mais rápido: o lock custa menos que a cópia da lista de listeners que cada
notificação fazia. Uma primeira medição do "antes" (279,6 ns na lista, erro de ±59) foi descartada
por ruído.

**Cobertura e mutação.** JaCoCo: core 74,2% das linhas (53,8% dos ramos), UI 52,2% (42,6%); os
caminhos OpenGL não rodam sem GPU. PIT antes dos testes direcionados: core 152 de 389 mutantes
mortos (39,1%), `JXNativeNode` + `JXRenderMemo` 89 de 115 (77,4%). O PIT mostrou que apagar a
chamada de notificação em `add` ou `remove` da lista não quebrava nenhum teste. Depois de testes para esses casos e de testes de layout com resposta
calculada à mão: core 172 de 389 (44,2%), UI 103 de 115 (89,6%).
Lição para o capítulo de método: teste de propriedade que compara dois caminhos não pega bug no
código que os dois compartilham (fórmula de tamanho preferido); para isso servem testes com
resposta conhecida (oráculo).

**Limitações.** O jcstress 0.16 não roda no Java 8 (usa `Thread.onSpinWait`); os testes rodam no
JDK 17 contra o mesmo bytecode Java 8. A imagem golden só vale para Windows 64 bits (fontes do
sistema); o NanoVG precisa de contexto OpenGL e ainda não tem golden. O japicmp fica para depois
da versão 0.1.0, quando houver versão anterior para comparar.

---

## 2026-09-25: Skia contra NanoVG na mesma JVM 64 bits

**Pergunta.** Vale trocar o Skia pelo NanoVG também em 64 bits e ficar com um renderer só?

**Medição.** Mesmo teste de stress (500 atualizações por componente, depois 600 quadros), JDK
17.0.12 x64, `-Djx.renderer` alternando a cada execução, JVM nova por execução, 5 execuções,
segundo monitor, 26% de CPU de outros processos. Dados:
`docs/ui-stress-results-renderers-x64-2026-09-25.csv`.

| Métrica (mediana) | Skia | NanoVG | Faixas se sobrepõem? |
|---|---:|---:|---|
| FPS sustentado | 120,2 | 120,3 | limitado pelo vsync |
| CPU por quadro | 3,0 ms | 2,7 ms | sim |
| Pior quadro | 12,5 ms | 12,4 ms | sim |
| Rajada de 2000 atualizações | 32,8 ms | 37,2 ms | sim |
| Início até o primeiro quadro | 787 ms | 716 ms | sim |
| Pico de working set | 161 MB | 156 MB | **não** (−4%) |
| Tamanho dos natives no Windows x64 | 9,2 MB | 0,4 MB | |

**Conclusão.** Em desempenho é empate: só a memória ficou fora do ruído, e a diferença é de 5 MB.
A troca só se justificaria por simplicidade e tamanho do pacote. Contra ela: o Skia tem o que a
paridade com o JavaFX vai exigir (sombras e desfoque como `DropShadow`/`GaussianBlur`, caminhos
complexos, filtros, codecs de imagem, texto com hinting), e o NanoVG tem só o básico (gradientes e
sombra de caixa simples). Decisão do autor: manter Skia em 64 bits e NanoVG em 32 bits.

## 2026-09-25: texto medido de verdade (primeiro passo da UI nativa no nível do JavaFX)

**Decisão do autor.** A 1.0 terá a UI nativa no nível do JavaFX. A ordem escolhida parte do
texto, porque tudo depende dele: sem medir texto, nenhum tamanho de layout é real.

**Problema.** O layout usava tamanhos fixos no código (botão 120x32, texto 100x24, caixa de
seleção 160x24), e os dois renderers desenhavam com fontes diferentes: Skia com a fonte padrão a
13 px, NanoVG com Segoe UI a 15 px. O mesmo programa tinha outra aparência em 32 e em 64 bits, e
textos longos vazavam dos botões.

**Estratégia.** `JXTextEngine` com HarfBuzz pelo LWJGL (o `HarfBuzz` do LWJGL carrega o FreeType
junto; cerca de 1,5 MB de natives por plataforma, com x86). O HarfBuzz mede o texto shaped (kerning,
ligaduras, acentos, outros alfabetos) direto das métricas OpenType, só com CPU: sem janela, sem GPU,
e com o mesmo resultado em 32 e 64 bits. Os dois renderers passam a desenhar com o mesmo arquivo de
fonte e tamanho. O Skia desenha texto shaped (`Shaper` do próprio Skia, que também usa HarfBuzz) em
vez de `drawString`, que ignorava o kerning. O layout usa os padrões do JavaFX: `TextField` com 12
colunas da largura de "W", `TextArea` com 40x10; botões, rótulos e caixas de seleção medem o texto.
Campos de entrada não mudam de tamanho ao digitar, então digitar não refaz o layout.

**Validação.**

- O Skia chegou a desenhar "AVATAR Wave" 4 px mais largo do que o layout mediu, porque o
  `drawString` não aplica kerning. Com texto shaped, as 6 frases de teste concordam em menos de 0,5 px.
- O snapshot de layout gerado no Java 17 x64 bateu idêntico no Java 8 x86: o layout não depende
  mais da arquitetura.
- O teste de propriedade do reconcile passou a exercitar a invalidação por texto: trocar o texto de
  um rótulo agora muda o tamanho dele, e o reconcile precisa refazer o layout.

**Custo** (JMH, tela de 1001 nós, JDK 17):

| Caso | Tamanho fixo | HarfBuzz sem cache | HarfBuzz com cache de largura |
|---|---:|---:|---:|
| Montar do zero | 65,5 µs | 759,7 µs | 68,3 µs |
| Reconcile de um rótulo | 1,1 µs | 3,4 µs | 2,7 µs |

O shaping custa cerca de 0,7 µs por texto. Como sem hinting a largura escala linearmente com o
tamanho, o cache guarda a largura por texto uma vez só, e interfaces repetem muito os mesmos
textos. A primeira vez que cada texto aparece continua custando os 0,7 µs. O gate da CI compara
razões e não pegaria essa piora absoluta; fica registrado como limitação do gate.

**Encontrado na janela real.** Numa coluna, o botão ocupa a largura toda. No JavaFX, o `Button`
tem largura máxima igual à preferida e não estica numa `VBox`. É o próximo passo (motor de layout).

**Limitação.** O NanoVG desenha com o shaping próprio (stb_truetype, só a tabela `kern`); em fontes
com kerning só na tabela GPOS, o texto pode sair poucos pixels mais largo que o medido no 32-bit.

## 2026-09-25: layout com os algoritmos do JavaFX, testado contra o JavaFX real

**Problema.** O layout nativo tinha só tamanho preferido: numa coluna, todo filho ocupava a largura
toda (um botão virava uma faixa azul de ponta a ponta), e filhos que não cabiam ficavam com tamanho
zero. O JavaFX tem tamanho mínimo, preferido e máximo por nó, prioridade de crescimento
(`Priority.ALWAYS`/`SOMETIMES`), alinhamento e `fillWidth`/`fillHeight`.

**Decisão.** Implementar os algoritmos do próprio JavaFX (`VBox`, `HBox`, `StackPane`, JavaFX 21)
em vez de usar o Yoga, que implementa flexbox: o `Priority` do JavaFX não é o `flex-grow`, e o
Yoga não tem `GridPane`. O código-fonte do OpenJFX foi lido como especificação (não copiado:
GPLv2), inclusive o arredondamento para pixel: tamanhos para cima, posições para o inteiro mais
próximo, espaço extra distribuído em porções inteiras arredondadas para baixo, primeiro para
`ALWAYS`, depois `SOMETIMES`; quando falta espaço, todos encolhem até o mínimo e o resto transborda.

**Fatos medidos no JavaFX 21** (programa de sonda, Windows, fonte padrão System 12 px): Label,
Button, CheckBox, ComboBox, ProgressBar e ToggleButton têm máximo igual ao preferido; TextField,
PasswordField e Slider esticam na largura; TextArea e Region esticam nos dois eixos; a altura de
linha de Segoe UI 12 px é 17 px, que é `ceil(ascent) + ceil(descent)`. Com isso a fonte padrão
passou de 13 para 12 px e os tamanhos batem com o JavaFX: Label "Customers" 57x17, Button "Save"
41x25, TextField 149 (JavaFX: 148,5), ProgressBar 100x18, Slider 14 de altura. CheckBox ficou 1 px
menor.

**Teste diferencial.** `JXLayoutDifferentialTest` gera 1000 árvores aleatórias de
VBox/HBox/StackPane/Region com tamanhos mínimo/preferido/máximo, prioridades, alinhamentos,
espaçamentos e flags aleatórios, monta a mesma árvore no JavaFX real e no JX e exige posição e
tamanho idênticos em todos os nós. Passou nas 1000. Para confirmar que o teste é sensível, trocar o
arredondamento das porções (`floor` por `round`) o fez falhar, com o caso mínimo em 22 passos: uma
linha com duas regiões `ALWAYS` em 3 px (o JavaFX dá 1 e 2 px).

**Custo e otimização.** A primeira versão ficou 2,5x mais lenta para montar uma tela de 1001 nós
(62 contra 158 µs). O JFR mostrou a causa: ler as props de layout percorria o mapa imutável, criando
um objeto por entrada, e cada rótulo media também "..." (para o tamanho mínimo). Com `Map.forEach`
+ `switch` de string e a largura de "..." calculada uma vez: 108 µs. O reconcile de um rótulo foi de
2,8 para 7,8 µs, porque o algoritmo do JavaFX repassa todos os filhos quando um tamanho muda.

**Melhoria sobre o JavaFX.** No JavaFX, `requestLayout()` sempre sobe até a raiz. No JX, a mudança
para no primeiro nó cujos tamanhos mínimo/preferido/máximo saem iguais: o pai mantém o layout e só
a subárvore alterada é refeita. O teste de propriedade do reconcile cobre isso; desligar a descida
até os filhos sujos o fez falhar.

**Responsividade (sugestão do autor, 25/09).** O padrão fica igual ao JavaFX (para FXML e apps
existentes se comportarem igual e para o teste diferencial continuar tendo oráculo). Melhorias
opcionais planejadas: `overflow` (cortar ou rolar), quebra de linha em `row`, breakpoints e
prioridade de exibição com menu de itens ocultos.

## 2026-09-25/26: todos os containers de layout do JavaFX

**Escopo.** Depois de `VBox`/`HBox`/`StackPane`: padding e margens, `BorderPane`, `GridPane`,
`Pane`, `AnchorPane`, `FlowPane` e `TilePane`. Cada algoritmo foi portado lendo o código do
OpenJFX 21 como especificação (não copiado).

**Verificação.** O teste diferencial virou quatro propriedades de 1000 árvores aleatórias cada
(caixas com `BorderPane`; grids; `Pane`/`AnchorPane`; `FlowPane`/`TilePane` como raiz), montadas no
JavaFX real e no JX, com posição e tamanho idênticos em todos os nós. Para cada container, pelo
menos uma mutação proposital foi pega: margem ignorada no posicionamento, `BorderPane` sem o piso de
tamanho mínimo, spans ignorados no grid, arredondamento dos percentuais do grid, resto da divisão
distribuído de 2 em 2 px, âncora direita sem o padding, condição de quebra do `FlowPane` e
alinhamento da última linha do `TilePane`.

**O `GridPane` é o mais complexo:** cerca de 1.500 linhas no OpenJFX, com uma estrutura própria
(`CompositeSize`) para células que ocupam várias linhas ou colunas, distribuição por percentual com
acúmulo de resto, e um laço de crescer/encolher diferente do da `VBox` (trata o resto da divisão
pixel a pixel). As versões de linha e de coluna foram comparadas por diff depois de renomear os
identificadores; eram simétricas, e a portagem ficou com um eixo parametrizado.

**Dois achados do próprio teste.**

1. O gerador alterava objetos já gerados (`props.put`). O jqwik reaproveita esses objetos ao reduzir
   um caso, e surgiu um `BorderPane` com dois filhos em `top`, algo que o gerador não produziria.
   Correção: os geradores passaram a criar cópias. Lição: em teste de propriedade, o gerador precisa
   ser imutável.
2. Num `AnchorPane` menor que as âncoras, o JavaFX dá largura negativa ao filho (-2), e o filho do
   filho fica em x = -1. O JX cortava tamanhos para 0. Agora aceita tamanho negativo, como o JavaFX.

**Limitações registradas.** *Content bias* (altura que depende da largura, caso do `FlowPane`,
`TilePane` e texto com quebra) ainda não é repassado pelos pais: um `FlowPane` dentro de uma `VBox`
recebe a altura do `prefWrapLength`, não a da largura real. Alinhamento por baseline,
`USE_PREF_SIZE` nas restrições e span `REMAINING` também faltam.

**Custo.** A máquina estava cerca de 2x mais lenta nesta medição (o commit anterior, que media
62 µs, deu 130 µs), então vale a razão contra o commit anterior nas mesmas condições: montar a tela
de 1001 nós ficou 2,3x mais caro e o reconcile de um rótulo 3,0x. Duas otimizações guiadas pelo JFR
entraram: a comparação de props no reconcile passou a percorrer só as chaves presentes, e o tipo do
container virou um inteiro calculado uma vez (antes, cada layout comparava strings em cadeia).

## 2026-09-26: DeviceConfig como critério de fechamento da 1.0

**Decisão.** A 1.0 (e a prova de conceito do TCC) só fecha quando o DeviceConfig, sistema real da
empresa com 232 telas FXML feitas em parte no Scene Builder, rodar inteiro sobre o JXParallel. A
migração deve mudar o mínimo: trocar imports nos controllers (ou o nome da classe pelo prefixo JX),
com os FXML intactos. No DeviceConfig só se mexe em UI, e problemas antigos ficam como estão, para
que a comparação de desempenho antes e depois meça só a troca de framework.

**Inventário.** Uma varredura sobre a branch `dev` (detalhes em `docs/migracao-deviceconfig.md`)
mostrou que o sistema usa quase toda a API de controles do JavaFX, 1001 listeners, 208
`Platform.runLater`, 80 fábricas de células, CSS em 406 chamadas `setStyle`, ControlsFX
(`Notifications`, 71 arquivos) e API interna `com.sun.javafx` (`ScrollPaneSkin`, 32 arquivos). Isso
muda a escala da 1.0: de "controles básicos" para "API do JavaFX usada por um sistema real".

**Preparação.** Branch `feature/jx-parallel-refactoring` criada a partir de `dev` num worktree
separado, para não tocar no checkout de trabalho. O projeto compila sem mudanças com o JDK 8u51
32 bits. A suíte de testes do frontend é longa e só será rodada depois da migração; o estado
anterior é conferido manualmente se preciso.

**Fase 1: trocar os imports sem trocar a implementação.** Foi criado o módulo `jxparallel-fx`, com
uma classe `com.jxparallel.fx.X` para cada `javafx.X` usada pelo DeviceConfig, gerada por reflexão
sobre o JavaFX 8 e por enquanto herdando da classe do JavaFX. O `FXMLLoader` do JX troca as classes
dos FXML por meio de um ClassLoader, e o `FXMLLoader` do JavaFX repassa esse ClassLoader aos
`fx:include`, então os 232 FXML não mudaram. Um script trocou os imports de 438 arquivos sem tocar
em nenhum outro byte (o encoding de cada arquivo ficou igual).

Resultado medido: o código compila sem erros, os testes compilam, e as 232 telas carregadas com o
código original e com o migrado dão o mesmo resultado tela a tela (229 carregam, as mesmas 3 falham
por falta de ambiente). 2582 dos 3867 imports `javafx.*` (67%) viraram JX, e 314 dos 512 nós criados
pelos FXML já são classes JX.

**O que não deu para trocar, e por quê.** A compilação mostrou quatro grupos: tipos que o próprio
JavaFX devolve (`getItems()` devolve um `ObservableList` do JavaFX, `start(Stage)` recebe o `Stage`
dele), classes base (o `Label` do JX herda do `Label` do JavaFX, então não é um `Node` do JX, e o
Java não tem apelido de tipo), genéricos que precisam bater exatamente (`Callback<TableColumn,
TableCell>`), e o que não se estende (enums, eventos, classes `final`, a anotação `@FXML`). Os quatro
têm a mesma causa: herdar do JavaFX. A fase 2 resolve todos, porque as classes JX passam a formar uma
hierarquia própria que devolve os próprios tipos. Isso responde à pergunta da migração: trocar só
imports é viável para o sistema inteiro, mas exige a API JX completa, não um verniz sobre o JavaFX.

**Fase 1, falha que a compilação não mostrou.** 32 controllers leem por reflexão o campo interno
`viewRect` do `ScrollPaneSkin` e fazem cast para `StackPane`. Com a fase 1, esse `StackPane` passou a
ser a classe JX, então o código compila mas daria `ClassCastException` quando a skin fosse criada, o
que só acontece com a tela exibida (o teste de carga dos FXML não chega lá). É mais um caso de tipo
devolvido pelo JavaFX, e a fase 2 o resolve do mesmo jeito que os outros.

**Duas camadas de componentes (decisão do autor).** Projetos que já usam JavaFX migram pela camada
de compatibilidade, `com.jxparallel.fx`, com os mesmos nomes do JavaFX (`Label`, `VBox`) e o mesmo
contrato. Projetos novos, ou refatorações maiores, usam a camada nativa `com.jxparallel.ui`, com
prefixo JX (`JXLabel`, `JXButton`), comportamento mais completo e liberdade para divergir do JavaFX.
A camada de compatibilidade vai ser implementada sobre a nativa, para não duplicar código.

**Fase 2: hierarquia própria.** Cada classe JX guarda o objeto JavaFX que a implementa por enquanto
(o peer) e converte tudo que cruza a fronteira: nós, listas, enums, eventos e listeners
(`com.jxparallel.fx.Fx`). Quando o objeto foi criado pelo JX, o peer é uma subclasse JavaFX que
repassa ao objeto JX os métodos que a aplicação sobrescreve (`updateItem`, `call`, `start`...). Assim
o JavaFX nunca devolve um tipo seu para a aplicação, e os quatro grupos da fase 1 deixam de existir.
Depois, a implementação por trás de cada classe troca do peer JavaFX para o componente nativo, sem
mudar a API.

**Fase 2, como foi construída.** Um gerador lê por reflexão os pacotes principais do JavaFX 8 e
escreve 424 classes JX e 252 peers; à mão ficaram só o que é ponte com o launcher (`Application`,
`Preloader`), a lista observável, o `FXMLLoader` e os equivalentes das 4 classes internas que o
DeviceConfig usa. O `FXMLLoader` passou a usar uma engine própria de FXML (a do cache de templates,
tornada independente do JavaFX), porque o do JavaFX só reconhece a sua anotação `@FXML` e entregaria
eventos JavaFX aos métodos dos controllers.

**O backend também mudou, por decisão do autor.** Cinco classes de modelo do backend expõem
`ObservableList` e `SimpleStringProperty` do JavaFX, e o frontend passa e recebe esses objetos. Com
o backend intocado sobravam 5 erros nessa fronteira; o autor escolheu aplicar a mesma troca de
imports nessas classes em vez de manter os pacotes de dados do JavaFX como base comum.

**Resultado medido.** Não restou nenhum import de `javafx`, `com.sun.javafx` ou `org.controlsfx` no
frontend nem no backend; além deles mudaram só 3 nomes qualificados no código e os `pom.xml`. O
código e os testes compilam. As 232 telas, carregadas pelo `FXMLLoader` original e pelo do JX, dão o
mesmo resultado tela a tela (229 carregam; as mesmas 3 falham, com a mesma exceção, por falta de
banco e login), e os 512 nós criados pelos FXML são objetos JX. Isso confirma que a migração só por
troca de imports é possível para um sistema real, desde que a API compatível tenha hierarquia
própria. Falta abrir o sistema de verdade e rodar a suíte de testes dele.

**Erros que o teste de carga revelou.** Quatro erros da engine de FXML nova apareceram só ao carregar
as telas reais: o `fx:id` de um `<fx:include>` era procurado pelo namespace declarado no próprio
elemento (22 telas ficaram sem os painéis incluídos), `<GridPane.margin>` era tratado como objeto,
`<Insets/>` sem atributos não usava os valores padrão como o `FXMLLoader` faz, e lambdas de
`UnaryOperator<TextFormatter.Change>` recebiam o objeto do JavaFX. Os testes unitários sintéticos
não tinham pegado nenhum deles, o que reforça o uso do sistema real como teste de aceitação.

**Primeira execução real (IntelliJ, banco de homologação).** O sistema abriu, o login passou e o
Hibernate conectou. A primeira tela com tabela quebrou: o `PropertyValueFactory` do JavaFX chama por
reflexão o `nameProperty()` do modelo, que agora devolve uma property JX, e faz cast para a sua. É
o mesmo tipo de fronteira de antes, só que escondido numa reflexão do próprio JavaFX, que nenhuma
compilação mostra. O `PropertyValueFactory` do JX passou a ser escrito à mão, fazendo a busca do
lado JX.

**Travamentos: medidos, e a causa não era o JX.** Com o sistema aberto, a busca de fichas e a
abertura de uma ficha grande travavam a tela. Amostrando a pilha da thread de UI a cada meio segundo
durante o uso (147 amostras, 37 com a tela ocupada), 32 estavam esperando o Postgres e nenhuma tinha
código do JX no topo. A abertura da ficha fazia uma consulta por categoria e outra por atributo
(padrão N+1), centenas de idas ao banco na thread de UI, e o método estava copiado em 23 controllers.
Na tabela de fichas, cada atualização de célula decodificava o PNG do botão, criava um `Tooltip`
novo (uma janela popup) e varria a tabela inteira para saber se havia versão mais nova, o que dá
custo quadrático no número de fichas.

**Correções de arquitetura no DeviceConfig (pedido do autor, só na branch `-jx`).** A árvore da
ficha passou a vir em 3 consultas numa sessão (`RecordCategoryRepository.loadTree`), e as 23 cópias
chamam esse método. Na tabela, as imagens ficam em cache, o tooltip só é recriado quando o texto
muda e a última versão de cada produto é calculada uma vez, quando a tabela recebe os itens.
Consequência para a metodologia: a partir daqui a branch `-jx` difere da original em duas coisas (o
framework e essas correções). Para medir só o efeito do JXParallel, a comparação precisa ser feita
com as mesmas correções nos dois lados, ou com a medição anterior a elas.

**Erro de medição, corrigido.** A tela continuou "Não está respondendo" depois dessas correções, e
duas rodadas de amostragem disseram que a thread de UI nunca estava ocupada. O erro era do filtro:
toda pilha da thread de UI termina no laço nativo `_runLoop`, porque o código Java roda dentro dele,
e o filtro descartava qualquer pilha que contivesse esse frame. O critério certo é olhar só o topo
da pilha. Refeita a análise das mesmas 315 amostras, 102 estavam ocupadas. Lição para a metodologia:
um amostrador que nunca encontra nada precisa ser testado contra um caso conhecido antes de servir
de evidência.

**A causa real da busca lenta.** Das 102 amostras ocupadas, a maioria estava em `RecordRow.setUpdate`,
chamado para cada ficha do resultado na thread de UI: para cada versão, uma varredura da lista
inteira de modelos de versão com dois `toUpperCase()` por comparação (custo fichas x versões x
modelos). A lista passou a ser indexada uma vez por (versão, linha, modelo), mantendo a regra do
primeiro encontrado. O mesmo método compara duas `String` com `==` (sempre falso na prática), o que
força o caminho caro para todas as fichas; é um bug antigo que muda comportamento se corrigido, então
ficou registrado e não foi alterado.

**Banco fora da thread de UI, num ponto só (pedido do autor).** O DeviceConfig tem 58 pontos que
abrem sessão Hibernate, todos por `HibernateConnection.getInstance()`, e a maioria roda a partir de
cliques. Em vez de reescrever cada fluxo com `Task` e callbacks, a sessão devolvida passou a ser um
proxy (`OffFxThread.wrap`, novo no JXParallel): chamadas que vão ao banco (`list`, `get`, `save`,
`commit`...) feitas na thread de UI rodam numa thread de fundo, enquanto a thread de UI continua
processando eventos num laço aninhado, ignorando entrada do usuário e com cursor de espera. O código
que chama continua síncrono e igual; a janela continua redesenhando e o Windows não a marca como
"Não está respondendo". A criação da `SessionFactory` (cerca de 3 s no login) também saiu da thread
de UI. Custo conhecido: enquanto espera, eventos já agendados (`runLater`, fim de outras `Task`) podem
rodar; os laços aninhados saem na ordem certa mesmo nesse caso. Isso é um recurso que o JavaFX não
oferece e entra como argumento da camada JX.

**Primeira comparação medida: JavaFX x JXParallel, ambos com as mesmas otimizações.** Para isolar o
framework, foi criada a branch `feature/javafx-optimized-baseline` a partir de `dev`, com as mesmas 27
mudanças de aplicação da `-jx` (inclusive uma cópia do `OffFxThread`, que só usa API do JavaFX), mas
com JavaFX puro e o backend original. O roteiro `scripts/DeviceConfigBench.java` roda nas duas o
mesmo trabalho: carregar as 232 telas, pesquisar fichas (termo "10", 402 resultados, com o
`setUpdate` de cada linha) e abrir a ficha 7816 (Zeus NG, a de mais atributos). Foram 5 repetições,
no JDK 8u51 32 bits contra o banco de homologação. A tabela traz a mediana das repetições 2 a 5;
os dados brutos estão em `docs/deviceconfig-bench-2026-09-26-*.csv`.

| Passo | Métrica | JavaFX | JX | JX/JavaFX |
|---|---|---:|---:|---:|
| 232 telas | tempo | 3.523 ms | 638 ms | 0,18 |
| 232 telas | CPU | 3.758 ms | 508 ms | 0,14 |
| 232 telas | memória alocada | 1.310 MB | 162 MB | 0,12 |
| Pesquisa | tempo | 670 ms | 630 ms | 0,94 |
| Pesquisa | CPU | 562 ms | 484 ms | 0,86 |
| Abrir ficha | tempo | 1.080 ms | 836 ms | 0,77 |
| Abrir ficha | CPU | 406 ms | 164 ms | 0,40 |
| Abrir ficha | memória alocada | 105 MB | 17 MB | 0,16 |
| Todos | heap depois do GC | 19–25 MB | 32–36 MB | cerca de 1,5 |

Leitura: o ganho do JX vem quase todo do `FXMLLoader` com cache de template (cada FXML é interpretado
uma vez). A renderização ainda é a do JavaFX por baixo (fase 2a), então este número não mede o
desenho nativo. Na pesquisa, dominada por banco e pelo `setUpdate`, as duas empatam. O custo do JX é
memória retida: cerca de 12 a 15 MB a mais de heap, pelos templates em cache e pelos objetos JX. Na
primeira execução de cada passo (fria) a diferença é menor (telas: 4,7 s x 4,0 s) e a inicialização
até a primeira tela foi mais lenta no JX (950 ms x 378 ms, uma amostra de cada), pelo carregamento
das classes geradas. O maior intervalo sem redesenhar a tela ficou em 16 a 55 ms nas duas versões,
porque ambas usam o `OffFxThread`. Limitações: uma máquina, banco remoto (ruído de rede), 4 amostras
quentes, e as 232 telas carregadas fora da thread de UI pelo roteiro.

**Medição repetida, para ter números citáveis.** Quatro rodadas alternadas (JavaFX, JX, JX, JavaFX)
de 10 repetições cada, com o DeviceConfig fechado e sem outro processo do roteiro na máquina (a rodada
anterior teve um processo de teste parado, sem trabalhar, ao lado). A tabela traz a mediana das
repetições 2 a 10 das duas rodadas de cada versão (18 amostras), com o intervalo entre os percentis
10 e 90. Os dados brutos estão em `docs/deviceconfig-bench-2026-09-26-r2-*.csv`.

| Passo | Métrica | JavaFX | JX | JX/JavaFX |
|---|---|---:|---:|---:|
| 232 telas | tempo | 3.753 ms (3.308–4.986) | 582 ms (491–664) | 0,16 |
| 232 telas | CPU | 3.906 ms | 515 ms | 0,13 |
| 232 telas | memória alocada | 1.310 MB | 162 MB | 0,12 |
| 232 telas | tempo de GC | 227 ms | 44 ms | 0,19 |
| Pesquisa | tempo | 871 ms (658–989) | 637 ms (577–825) | 0,73 |
| Pesquisa | CPU | 758 ms | 523 ms | 0,69 |
| Abrir ficha | tempo | 1.126 ms (1.059–1.207) | 858 ms (822–872) | 0,76 |
| Abrir ficha | CPU | 468 ms | 109 ms | 0,23 |
| Abrir ficha | memória alocada | 105 MB | 17 MB | 0,16 |
| Todos | heap depois do GC | 19–24 MB | 32–36 MB | 1,5–1,7 |

Primeira execução de cada passo (fria, duas amostras por versão): 232 telas 5,3–5,8 s no JavaFX e
3,6–3,9 s no JX; abrir a ficha 1,13–1,19 s x 0,95 s; pesquisa 0,63–0,74 s x 0,90–0,91 s (o JX mais
lento no frio, pelo carregamento das classes geradas). Inicialização até a primeira tela: 363 e
867 ms no JavaFX, 466 e 475 ms no JX; a medição anterior (950 ms no JX) foi um ponto fora da curva,
e com duas amostras não há diferença clara. As rodadas `a` e `b` concordam (abrir ficha: 858/862 ms
no JX, 1.090/1.175 ms no JavaFX). A pesquisa oscila mais (é dominada pelo banco remoto); nesta
medição o JX saiu mais rápido, na anterior empatou.

**Fase 2b: o tamanho medido antes de começar.** Das 232 telas, só uma usa apenas elementos que o
`jxparallel-ui` nativo já desenha. O `TitledPane` aparece em 193 telas e é o maior bloqueio. Somando
controles na ordem de maior impacto, a cobertura passa a 81 telas com `TitledPane`, `Accordion` e
`Separator`, a 128 com `ScrollPane` e `Spinner`, a 205 com `ListView` e `TableView` e a 232 com os
demais. Há ainda CSS (43 telas e 448 chamadas `setStyle`) e 41 usos de janelas e diálogos. O plano
completo está em `docs/migracao-deviceconfig.md`.

**Fase 2b, primeiro passo: o modo nativo de ponta a ponta.** Decisão do autor: um interruptor global
(`-Djx.backend=native`) e validação tela a tela. No modo nativo, cada classe JX de nó guarda um
modelo (`NativeModel`) em vez de um componente JavaFX. Getters, setters, `xxxProperty()`, listas e os
setters estáticos dos layouts são servidos de forma genérica a partir desse modelo, com properties do
`javafx.base` (Java puro, sem renderização), então bindings e listeners do controller continuam
funcionando. O modelo vira a árvore de `JXElement` que o `jxparallel-ui` desenha. O que ainda não tem
tradução fica registrado numa lista, em vez de derrubar a tela. A ferramenta
`scripts/NativeScreenCheck.java` carrega um FXML do DeviceConfig com o controller real e grava a imagem
nos dois modos.

Primeira tela, `users/FormUserDialogView.fxml`, no JDK 8u51 32 bits: carregou no modo nativo com o
controller e a consulta ao banco, sem faltar nenhum método da API, e foi desenhada pelo NanoVG
(`docs/assets/deviceconfig-form-user-{javafx,native}.png`). Depois de três correções o layout ficou
idêntico ao do JavaFX:
- Um nó `visible="false"` continua ocupando espaço no JavaFX, e só `managed="false"` o tira do layout.
- O `Label` precisa levar as âncoras e os tamanhos.
- O `promptText` aparece enquanto o `ComboBox` não tem valor.

O que falta ali é só pintura (bordas do Modena, seta e alinhamento do texto no `ComboBox`).

**Dois bugs do JXParallel que a tela real expôs.**
1. **Tamanhos do Scene Builder.** O layout nativo transformava o `-Infinity` (USE_PREF_SIZE, presente em
   123 telas) em 0. Além disso, o máximo de um controle não acompanhava a largura preferida definida no
   FXML. O teste diferencial não pegava porque só gerava `Region`. Ganhou uma propriedade nova, com
   `Button` de verdade (skin e CSS do Modena) e esses valores: 500 casos idênticos ao JavaFX; com a
   correção desfeita, ela falha.
2. **Skia no Java 8.** O Skia (skija 0.116) derruba a JVM ao carregar a biblioteca no Java 8 64 bits
   (8u202); no Java 17 funciona. A regra do JXParallel era "Skia em JVM 64 bits" e nunca tinha sido
   testada no Java 8 x64. Passou a ser: Skia no Java 9 ou superior em 64 bits, NanoVG no resto. Para
   o DeviceConfig nada muda, porque ele usa o NanoVG no Java 8 32 bits. A captura sem janela visível
   foi feita com NanoVG numa janela OpenGL escondida.

**As 232 telas no modo nativo, em lote.** `scripts/NativeScreenBatch.java` carrega todas as telas numa
JVM só, no modo nativo, com os controllers reais, desenha cada uma com o NanoVG e registra por tela o
que faltou. Três rodadas, cada uma guiada pela anterior:

| Rodada | Carregam | Com erro | O que mudou antes dela |
|---|---:|---:|---|
| 1 | 85 | 147 | modo nativo só para subclasses de `Node` |
| 2 | 85 | 147 | a mesma, agora gravando a pilha de cada erro |
| 3 | 229 | 3 | ver abaixo |

Os erros da rodada 2 tinham duas causas. A primeira eram getters devolvendo `null` para objetos que o
JavaFX cria junto com o controle: o modelo de seleção (`comboBox.getSelectionModel().select(...)`) e o
`editor` do `Spinner` (usado pelo `SpinnerUtils` do DeviceConfig). A segunda eram classes que não são
`Node` mas guardam nós (`Tab.setContent`, `Scene`, `Stage`). Entraram modelos de seleção nativos
(simples, sincronizado com `value`, e múltiplo), o `editor`, a `SpinnerValueFactory` criada pelo
construtor, e o modo nativo passou a valer, por fecho transitivo, para toda classe cuja API referencia
nós: 168 das 423 classes.

Na rodada 3, das 3 telas com erro, duas foram o banco de homologação sumindo da rede
(`UnknownHostException`). A terceira expôs outra limitação conhecida do layout nativo: o
`GridPane.columnSpan="REMAINING"` virava um array de tamanho `Integer.MAX_VALUE`. Ele foi implementado
como no JavaFX, contando como 1 para dimensionar a grade e indo até a última coluna. O teste diferencial
passou a gerar `REMAINING` e na hora pegou um detalhe: o JavaFX só aplica o `vgrow` de filhos com span
literal 1, então `REMAINING` sobre uma linha só não cresce. Corrigido; as 5 propriedades seguem
idênticas ao JavaFX.

O que falta agora é quase só desenho: `TitledPane` aparece em 193 telas, `ScrollPane` em 24,
`ProgressIndicator` em 9, `Separator` em 5 e `ImageView` em 4. Da API faltou só
`ToggleGroup.selectToggle` (2 telas).

## 2026-09-28/29: fase 2b concluída (desenho nativo de toda a API usada)

**O que entrou.** Com `-Djx.backend=native`, cada nó JX do `jxparallel-fx` é um `NativeModel`
desenhado pelo `jxparallel-ui`. Entraram todos os controles da lista da fase 2b (inclusive
`ListView` e `TableView` virtualizados, com fábricas de células chamadas por reflexão no objeto JX,
ordenação, seleção simples e múltipla e colunas restritas), eventos com a semântica do JavaFX
(filtros da raiz para baixo, handlers do alvo para cima, `consume()`, aceleradores, Tab, botões
padrão e de cancelamento, arrastar e soltar com dragboard próprio), um subconjunto de CSS com
especificidade, diálogos num loop de eventos aninhado com a ordem de botões do Windows,
`FileChooser` via tinyfd, notificações do ControlsFX, modalidade de janelas e as transições. O
desenho de todos os elementos está num único código (`JXPaint`) sobre as primitivas que Skia e
NanoVG implementam (`JXPainter`), então os dois renderizadores desenham as mesmas formas.

**Achado: a altura de linha do JavaFX não sai da fonte.** O `Label` do JavaFX tem 17 px com Segoe UI
a 12 px e 25 px a 16 px; as métricas da fonte escaladas dão 15,96 e 21,3. O JavaFX usa métricas com
hinting do DirectWrite, que não se reproduzem a partir das unidades da fonte (a tentativa com a
tabela VDMX também não bateu). A solução foi medir no próprio JavaFX em execução: o modo nativo
cria um `Label` numa `Scene`, aplica o CSS e lê a altura e a linha de base uma vez por tamanho, e
também a largura de textos em negrito ou fora dos 12 px. Com isso os testes diferenciais de
tamanhos de controles passaram a bater em todos os casos.

**Achados dos testes diferenciais de grade.** Duas divergências do `GridPane`: a distribuição de
espaço de um filho com span usava o máximo já resolvido em vez do máximo declarado (corrigido), e
um filho que ocupa uma faixa de tamanho fixo (`USE_PREF_SIZE`) numa grade centralizada fica 1 px
deslocado em relação ao JavaFX (documentado como limitação; o gerador de casos evita esse caso).

**Imagens de referência e gravações.** Uma imagem com todos os controles em todos os estados e três
gravações exatas das primitivas de desenho (cada chamada de `JXPainter` vira uma linha de texto).
Revisar a imagem nova mostrou dois defeitos: dicas (`Tooltip`) sem tamanho, só com o texto branco
sobre a janela, e rótulos com `wrapText` que nunca quebravam linha (a condição comparava a largura
já cortada com reticências). O rótulo com quebra agora é comparado com o JavaFX num `VBox`.

**Teste de mutação.** No `jxparallel-ui` a primeira rodada sobre o código novo de layout, texto e
teste de clique matou 60% dos mutantes (549 de 921). Os sobreviventes eram bordas exatas (clique
meio pixel dentro ou fora, cursor no fim da linha, texto exatamente da largura da linha) e controles
em (0, 0), onde `x + padding` e `x - padding` não se distinguem. Depois dos testes de bordas e das
gravações: 1822 de 1922 (95%), incluindo o `JXPaint`. Os sobreviventes também apontaram código morto
(dois ramos iguais, `viewW -= 0`, parâmetro sem uso, um `default` inalcançável), removido. No
`jxparallel-fx` (runtime nativo, JDK 8): a primeira rodada matou
46% (1523 de 3279), com 861 mutantes em código que nenhum teste alcançava: teclado de controles
inteiros, barras de rolagem, métodos dos modelos de seleção, tipos de diálogo, notificações e boa
parte da API que as aplicações chamam direto. Com nove suítes novas: 2463 de 3174 (78%, força dos
testes 82%). O `register()` ficou fora: roda uma vez, no inicializador estático, então o mutante
trocado a quente pelo PIT nunca executa (as lambdas que ele registra são testadas).

**O que os testes de mutação acharam de errado de verdade.** Escrever os testes contra o
comportamento documentado do JavaFX revelou 16 divergências, todas corrigidas. As mais sérias: um
handler que consumia o evento impedia os outros handlers do mesmo nó (no JavaFX todos rodam; o
consumo só impede o próximo nó); getters de valores nunca definidos devolviam o zero do Java em vez
do padrão do JavaFX (um `TitledPane` não estava expandido, o máximo de um `Slider` era 0), agora
lidos de um protótipo da classe JavaFX; a fábrica de células do `ComboBox` recebia o próprio combo
em vez de uma `ListView` (quem declarava a lambda com `ListView` via células vazias); os handlers
`onShowing`/`onHidden` de diálogos nunca rodavam; e o `vvalue` do `ScrollPane` ignorava
`vmin`/`vmax`.

**O que a troca de import não resolvia.** `FadeTransition` recebe um nó, então no modo nativo é um
modelo nativo, mas herda `play()` de `Animation`, que não é: o gerador passou a acessar o par JavaFX
dessas superclasses por `Fx.peerAs`, e o modo nativo entrega uma animação JavaFX real que escreve as
propriedades do nó. E os 32 controllers que leem `ScrollPaneSkin.viewRect` por reflexão: o
`ScrollPane` exibido no modo nativo recebe um skin cujo `viewRect` existe.

**Falta.** Rodar as 232 telas do DeviceConfig no modo nativo e comparar com o JavaFX, o que depende
do banco de homologação.

## 2026-09-29: medição para o TCC (JavaFX puro, API JX sobre JavaFX e JX nativo)

**Pergunta.** Quanto custa ou economiza rodar uma aplicação no modo nativo do JXParallel, em
relação à mesma aplicação no JavaFX? As telas reais do DeviceConfig precisam do banco de
homologação, então a medição usa uma tela montada como as dele: um acordeão de 6 painéis com 60
campos (texto, combo, check box, spinner, date picker), uma tabela de 10 mil registros, uma lista e
abas. O mesmo código roda em três pilhas: JavaFX puro (o DeviceConfig de hoje, gerado trocando só
os imports), a API JX sobre JavaFX (fase 1) e o JX nativo (fase 2b). Foram 7 execuções por pilha,
alternadas, uma JVM nova por execução, com a memória do processo amostrada por fora, no JDK 8u51
32 bits (o do DeviceConfig) e no 8u421 64 bits. Detalhes e tabelas completas em
`docs/fx-backends-comparison-2026-09-29.md`.

**Resultado no 32 bits (nativo contra JavaFX puro).**

| Métrica | JavaFX puro | JX nativo | Diferença |
|---|---:|---:|---:|
| Carregar 50 registros nos 60 campos (trabalho da aplicação) | 120 ms | 18 ms | -85% |
| 30 trocas de aba | 477 ms | 322 ms | -33% |
| 200 rolagens da tabela | 3216 ms | 1684 ms | -48% |
| Intervalo entre quadros p99 | 7,9 ms | 4,4 ms | -44% |
| CPU total do processo | 5,72 s | 5,03 s | -12% |
| Classes carregadas | 3550 | 3210 | -10% |
| Pico de heap | 58,8 MB | 50,6 MB | -14% |
| Working set de pico | 141 MB | 159 MB | +12% |
| Total alocado | 312 MB | 933 MB | +199% |
| Montar a tela até o primeiro quadro | 709 ms | 835 ms | +18% |

No 64 bits o nativo gasta 26% menos CPU no total e 47% menos atualizando a cada quadro, com o
mesmo working set.

**Onde o nativo perde, e por quê.** Aloca de 2 a 3 vezes mais, porque cada quadro reconstrói a
árvore de elementos da tela visível e a reconcilia, enquanto o JavaFX só mexe nos nós que
mudaram; no 32 bits isso vira 4 vezes mais coletas de lixo jovens. Também demora mais para montar
a tela (+18 a 22%) e para trocar os itens por 100 mil linhas. A próxima otimização é memorizar os
elementos de subárvores que não mudaram.

**A medição guiou correções.** A primeira rodada no 32 bits mostrou o nativo gastando 38% mais
CPU e alocando 7 vezes mais que a API JX sobre JavaFX. O profiler (`hprof`) apontou seis causas,
todas corrigidas:

- `getSimpleName()` sem cache no Java 8, chamado a cada chamada da API gerada;
- padrões do JavaFX buscados por reflexão com exceção a cada leitura;
- células de tabela chamando a fábrica de valores a cada quadro;
- `glfwMakeContextCurrent` a cada quadro;
- o JavaFX inicializando o Direct3D sem desenhar nada no modo nativo (agora `prism.order=sw`);
- cópias e boxing por quadro.

Resultado no nativo 32 bits: CPU de 7,92 para 5,03 s, alocação de 2,25 para 0,93 GB, coletas de
317 para 138 e working set de 186 para 159 MB.

**A camada JX sobre o JavaFX (fase 1) custa pouco:** 7 a 8% para montar a tela e 8 a 9% de
working set. Os ganhos vêm do backend nativo, não da migração em si.

**Ameaças à validade.** É uma tela no formato do DeviceConfig, não o DeviceConfig (sem banco,
controllers ou CSS da aplicação). Os dados são de uma única máquina, com monitor de 239 Hz. A CPU
do processo inclui as threads do driver de vídeo, que diferem entre Direct3D e OpenGL; as linhas
de CPU das threads da JVM as excluem.

## 2026-09-29 (tarde): renderização incremental

**Problema.** A medição da manhã mostrou que o modo nativo reconstruía, a cada quadro, a árvore
de elementos de toda a tela visível e a reconciliava, enquanto o JavaFX só sincroniza os nós que
mudaram. Por isso alocava 16 vezes mais que o JavaFX atualizando a tela a cada quadro.

**Solução.** Cada modelo nativo guarda o último elemento construído e um número de versão. Uma
mudança incrementa a versão do nó e dos ancestrais; um quadro reconstrói só os nós com versão nova
e devolve os outros como a mesma instância, que a reconciliação já pulava por identidade. O que
invalida um elemento:

- propriedade, lista ou estado do nó;
- nos casos que o CSS enxerga por seletores de descendente ou que os filhos herdam (classes, id,
  estilo, desabilitado, estados de pseudo-classe) e para um nó que mudou de pai, toda a subárvore;
- hover, pressionado e foco;
- o valor observado por uma célula de tabela, que agora escuta a propriedade da linha como o
  `TableCell`;
- a fábrica de valores do spinner;
- uma imagem que terminou de carregar;
- as folhas de estilo da cena, que invalidam tudo.

Os testes novos (`NativeIncrementalTest`) confirmam a reutilização por identidade e cada caminho
de invalidação.

**Achado de passagem.** Adicionar a um pai um nó que já tinha outro pai o deixava nos dois, e ele
era desenhado duas vezes. O JavaFX move o nó; agora o modo nativo também move.

**Resultado (32 bits, por quadro, medianas de 5 execuções).** Ao atualizar a cada quadro, a
alocação caiu de 360 para 38 MB e a CPU da thread da aplicação de 609 para 125 ms (o JavaFX puro
gasta 266 ms). A alocação total caiu de 933 para 481 MB e as coletas de 138 para 40 (o JavaFX puro
faz 36). A sessão do Windows estava bloqueada durante essas execuções, o que muda a cadência de
quadros dos dois lados; a comparação completa das três pilhas precisa ser repetida com a sessão
desbloqueada.

## 2026-09-29 (noite): os pontos em que o nativo ainda perdia

- **Montar a tela até o primeiro quadro (antes 18 a 22% mais lento que o JavaFX).** O tempo
  quase todo não era montar elementos. A thread de desenho só começava no primeiro `show()` e
  então carregava o driver OpenGL ao criar a janela, depois de a aplicação montar a tela. Agora o
  modo nativo começa esse trabalho em segundo plano assim que inicia (`JXDisplay.prewarm()`:
  GLFW, driver, fontes e classes de desenho), em paralelo ao `Application.start()`, como o JavaFX
  faz com a sua thread de renderização. No 32 bits, com a sessão bloqueada, o nativo ficou em
  cerca de 435 ms contra 670 ms do JavaFX puro; da partida da JVM ao primeiro quadro, 650 ms contra
  1050 ms.
- **Rolagem da tabela (alocava o dobro).** Linhas e células que saem da tela agora são
  reaproveitadas para as que entram, como no `VirtualFlow` do JavaFX, em vez de criar uma linha de
  células por linha exibida. A alocação caiu de 299 para 210 MB (JavaFX puro: 147 MB).
- **100 mil linhas.** O nativo empata com a API JX sobre JavaFX (124 contra 125 ms). A diferença
  para o JavaFX puro está nos objetos da própria aplicação: cada propriedade JX é um objeto sobre
  um par do JavaFX. É custo da API da fase 1, não do desenho nativo.
- **O quadro de 44 ms no 32 bits** precisa ser conferido com a sessão desbloqueada.

## 2026-09-29 (noite): rolagem que move os nós em vez de refazê-los

- **O problema.** A árvore de nós desenhados era reconciliada por posição. Ao rolar uma linha, o
  nó da posição 0 recebia o elemento da linha que estava na posição 1, e assim por diante. Todas
  as linhas visíveis eram atualizadas, mesmo sem nenhuma mudança nelas.
- **A solução (reconciliação por chave, como a `key` do React).** Todo elemento leva uma chave,
  que é o seu `NativeModel`. Quando os filhos têm chaves distintas, o `JXNativeNode` casa os
  filhos pela chave. Uma linha que continua na tela mantém o seu nó e só é movida pelo layout. O
  elemento dela é a mesma instância de antes, porque fica memorizado a partir da versão da linha,
  da seleção, da paridade, do foco e dos elementos das células. Assim a reconciliação a pula. Só
  as linhas que entram são atualizadas, como no `VirtualFlow`.
- **Regressão encontrada na medição.** A célula referencia a sua tabela (`tableView`), e essa
  referência era tratada como se a tabela tivesse mudado de pai. Com isso, a subárvore inteira da
  tabela, incluindo o pool de células, era invalidada a cada vez. A alocação da rolagem subiu para
  734 MB. A causa foi achada rastreando as pilhas das invalidações. Propriedades de referência
  deixaram de contar como troca de pai, e um teste cobre o caso.
- **Resultado (32 bits, sessão bloqueada).** Rolando de uma em uma linha, a alocação foi de
  36,2 MB (reconciliação por posição) para 31,1 MB (por chave); o JavaFX puro aloca 26,0 MB. Em
  saltos de 50 linhas, todas as linhas visíveis mudam, então a chave não ajuda: 206 MB contra
  147 MB do JavaFX. O resto da diferença é o elemento de cada célula reaproveitada, refeito porque
  ela passa a mostrar outro item.

## 2026-09-29 (noite): fechando a diferença da rolagem

- **Onde estava a alocação.** O benchmark passou a separar a alocação por thread. A thread da
  aplicação respondia por quase tudo (186 MB contra 131 MB do JavaFX); a thread de desenho nativa
  aloca menos que a do JavaFX. Os sítios do hprof contavam cerca de 8 vezes menos que o total, e o
  JFR do 8u421 grava no formato antigo. Por isso cada fase da renderização foi medida com
  `ThreadMXBean.getThreadAllocatedBytes`.
- **O que foi corrigido, por peso:**
  - Chaves de restrição de layout eram concatenadas a cada elemento (14 strings por célula).
  - Cada linha passava por `CellDataFeatures` e dois adaptadores proxy até o
    `PropertyValueFactory`, que agora é lido direto.
  - Configurar as células deixava obsoleto o elemento da tabela em construção, e o quadro seguinte
    visitava todas as células de novo.
  - O CSS recalculava escopo, cores e declarações por célula; agora há cache por renderização.
  - A reconciliação por chave criava dois mapas por linha.
  - `JXProps` passou de `LinkedHashMap` para um array plano.
  - Deixou de haver boxing nas propriedades numéricas e nas coordenadas.
- **Bug encontrado no caminho:** linhas selecionadas de tabela não apareciam selecionadas no modo
  nativo.
- **Resultado (32 bits, mediana de 3):**
  - Rolagem: 52 MB contra 148 MB do JavaFX puro; eram 206 MB antes.
  - Alocação total da execução: 185 MB contra 313 MB.
  - CPU da rolagem: 0,98 s contra 1,30 s.
  - O nativo ainda perde na troca de abas (6,0 contra 1,9 MB), nas 100 mil linhas (55 contra 49 MB,
    custo da API JX) e no pico de memória do processo (167 contra 147 MB).

## 2026-09-29 (noite): as últimas perdas

- **Troca de abas (6,0 contra 1,9 MB).** Havia duas causas:
  - Selecionar uma `Tab` invalidava o formulário inteiro dela. No JavaFX, a `Tab` não é o pai
    CSS do conteúdo; o pai é a região de conteúdo da `TabPane`. Agora só `disable` da aba chega
    ao conteúdo.
  - Os nós nativos da aba que saía eram descartados. A `TabPane` passou a guardá-los (até 16).

  Além disso, o JavaFX monta todas as abas junto com a cena. O nativo passou a montar as abas
  escondidas em tempo ocioso, uma por vez, 300 ms depois do primeiro quadro. Resultado: 1,24 MB
  contra 1,88 MB.
- **100 mil linhas (54,7 contra 48,7 MB).** Cada propriedade JX era um objeto JX mais um par
  JavaFX. As `Simple*Property` passaram a criar o par só quando necessário (listener, bind,
  JavaFX lendo); até lá guardam o próprio valor. Resultado: 42,2 contra 48,4 MB, com o heap vivo
  em 32,5 contra 46,7 MB. A API JX sobre JavaFX também ganhou.
- **CPU da abertura.** A thread de desenho gastava 453 ms porque criava dois contextos OpenGL: o
  descartável do pré-aquecimento e o da janela. A janela pré-aquecida agora vira a primeira
  janela. Nas threads Java, os dois empatam até o primeiro quadro (951 contra 952 ms); a sobra de
  cerca de 100 ms no processo está nas threads do driver e no JIT. O primeiro quadro sai 140 ms
  antes.
- **O que ficou.** O pico de working set é cerca de 6 MB maior (153 contra 147 MB) quando as abas
  são montadas em tempo ocioso. Um A/B mostrou que os bytes privados são iguais com e sem essa
  montagem, e menores que os do JavaFX (178 contra 205 MB). A diferença é paginação do Windows,
  não memória do processo.

## 2026-09-29 (madrugada): medição com a tela ligada

Com a sessão desbloqueada e a tela ligada a 239 Hz, as três pilhas foram medidas de novo com 10
execuções cada, em 32 e 64 bits, agora com quadros sincronizados.

Primeiro achado: em 32 bits o modo nativo tinha um quadro de cerca de 44 ms por execução. Foram
descartados, um a um, coleta de lixo, layout, troca de buffers, resolução do timer e texto. O
`-XX:+PrintCompilation` mostrou a causa: na JVM *client* de 32 bits, o invólucro de um método
nativo do NanoVG é gerado quando o método fica quente, e a thread que chamou espera cerca de 15 ms
por ele. Correção: aquecer esses métodos numa thread de fundo, um segundo depois do primeiro
quadro, só na JVM *client*. Tentativas rejeitadas: aquecer na thread de desenho (primeiro quadro
400 ms mais tarde), aquecer na partida (primeiro quadro em cerca de 920 ms) e aquecer em todas as
JVMs (a CPU das abas piorou em 64 bits).

Resultado (medianas, Holm): em 32 bits, pior quadro de 5,2 contra 15,9 ms, percentil 99 de 4,4
contra 7,8 ms, alocação total de 166 contra 313 MB, CPU total de 4,59 contra 5,75 s. Nenhuma
métrica de tempo, CPU ou memória ficou significativamente pior em nenhuma das arquiteturas. A
mediana do intervalo entre quadros em 32 bits ficou 0,4% maior (4,16 contra 4,14 ms), com taxa de
quadros maior no modo nativo. Dados em `docs/fx-backends-*-2026-09-29-vsync-n10*.csv`.

## Evolução das métricas principais

| Data | Métrica | JavaFX | JXParallel | Observação |
|---|---|---:|---:|---|
| 22/09 | Atualizar label 500x | 13.0 ms | 3.3 ms | teste inválido (não chegava à tela) |
| 24/09 | Atualizar label 500x | 11.6 ms | 30.0 ms | teste corrigido |
| 24/09 | Pico de RAM, teste de UI | 248.6 MB | 159.8 MB | Skia + OpenGL |
| 24/09 | CPU por quadro | 10.6 ms | 3.0 ms | 600 quadros, vsync |
| 24/09 | FXML 20 telas, quente | 1314 ms | 1170 ms | pool preso em 2 threads |
| 24/09 | FXML 20 telas, quente | 1314 ms | 442 ms | pool corrigido, 8 threads |
| 25/09 | FXML 20 telas, quente | 665 ms | 194 ms | máquina ociosa |
| 25/09 | FXML primeira tela, quente | 30 ms | 72 ms | todas em paralelo |
| 25/09 | FXML primeira tela, quente | 64 ms | 59 ms | primeira tela primeiro |
| 25/09 | CPU por quadro, Java 8 x86 | 3.9 ms | 1.5 ms | JavaFX 8 contra NanoVG |
| 25/09 | CPU por quadro, Java 17 x64 | 11.3 ms | 2.9 ms | JavaFX 21 contra Skia, FPS válido |
| 25/09 | Heap vivo após GC, x86 | 7.6 MB | 2.1 MB | JavaFX 8 contra NanoVG |
| 25/09 | Label 500x quente, x64 | 3.34 ms | 2.59 ms | após atualização incremental |
| 25/09 | Label 500x quente, x86 | 2.57 ms | 0.93 ms | após atualização incremental |
| 25/09 | Pior quadro NanoVG x86 | 31.1 ms | 23.6 ms | 10 execuções |
| 25/09 | FXML 20 telas, quente | 692 ms | 31 ms | template pré-interpretado |
| 25/09 | FXML uma tela, quente | 40.7 ms | 7.8 ms | template pré-interpretado |

Métricas de qualidade (JXParallel apenas; o JavaFX não tem contrato de concorrência):

| Data | Métrica | Antes | Depois | Observação |
|---|---|---:|---:|---|
| 25/09 | Estados proibidos no jcstress | 3.061.314 | 0 | 5 testes, cerca de 419 milhões de amostras no depois |
| 25/09 | `JXObservableList` add + remove | 105,9 ns | 64,9 ns | JMH, com um listener |
| 25/09 | `JXProperty.set` | 37,8 ns | 28,8 ns | JMH, com um listener |
| 25/09 | Mutantes mortos, core | 39,1% | 44,2% | PIT 1.15.8 |
| 25/09 | Mutantes mortos, `JXNativeNode` | 77,4% | 89,6% | PIT 1.15.8 |
| 29/09 | Mutantes mortos, layout/texto/clique nativos | 59,6% | 95,0% | PIT 1.15.8; o depois inclui o `JXPaint` (1922 mutantes) |
| 29/09 | Mutantes mortos, runtime nativo (`nativeimpl`) | 46,4% | 77,6% | PIT 1.15.8, JDK 8; 16 divergências do JavaFX corrigidas no caminho |
| 29/09 | CPU do processo, tela tipo DeviceConfig, JX nativo 32 bits | 7,92 s | 5,03 s | JavaFX puro: 5,72 s; 7 execuções, mediana |
| 29/09 | Alocação total, mesma medição | 2,25 GB | 0,93 GB | JavaFX puro: 0,31 GB |
| 29/09 | Alocação da rolagem da tabela, JX nativo 32 bits | 206 MB | 52 MB | JavaFX puro: 148 MB; mediana de 3 |
| 29/09 | Alocação total da execução, JX nativo 32 bits | 0,93 GB | 0,17 GB | JavaFX puro: 0,31 GB; mediana de 3 |
| 29/09 | Troca de abas, alocação, JX nativo 32 bits | 6,0 MB | 1,24 MB | JavaFX puro: 1,88 MB |
| 29/09 | 100 mil linhas, alocação, JX nativo 32 bits | 54,7 MB | 42,2 MB | JavaFX puro: 48,4 MB |
| 29/09 | Heap vivo após GC, JX nativo 32 bits | 44,7 MB | 32,5 MB | JavaFX puro: 46,7 MB |

## Ameaças à validade (para o capítulo de metodologia)

- Os dois lados não desenham a mesma coisa: o JavaFX aplica CSS, skins, texto LCD e um
  `ListView` real; o JXParallel desenha retângulos e texto.
- Carga de outros processos altera os tempos absolutos em até 2x.
- Sessão do Windows bloqueada limita a apresentação de quadros.
- A CPU de processo no Windows tem granularidade de 15.6 ms.
- A alocação é somada só sobre threads vivas no momento da leitura.
- Todos os números até aqui são de uma única máquina.

## Pendências

- Compilação de FXML para Java no build (removeria o custo da primeira carga).
- HarfBuzz/FreeType para texto e Yoga para layout.
- Benchmark de 100 mil linhas da tabela virtualizada nativa contra o `TableView`.
- Rodar as 232 telas do DeviceConfig no modo nativo (depende do banco de homologação).
- Imagem golden do NanoVG (precisa de contexto OpenGL fora da tela).
- japicmp na CI depois da versão 0.1.0.
- Subir a taxa de mutantes mortos do `AdaptiveWorkerPool` e do `JXParallelConfig`.
