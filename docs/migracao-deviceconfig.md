# Migração do DeviceConfig para o JXParallel

Critério de fechamento da 1.0: o DeviceConfig (frontend) inteiro roda sobre o JXParallel,
mudando o mínimo possível e no menor número de lugares. No DeviceConfig só se mexe em UI;
problemas pré-existentes ficam como estão. O projeto é o medidor real de desempenho.

Regras:

- Os 232 FXML existentes continuam funcionando sem edição, inclusive os feitos no Scene Builder.
- Nos controllers, a mudança aceita é trocar imports (`javafx.*` por `com.jxparallel.*`) ou o
  nome da classe por um com prefixo JX.
- Branch: `feature/jx-parallel-refactoring`, criada de `dev` (3c924c907) no worktree
  `C:\dev\repos\git\DeviceConfig\DeviceConfigFrontend-jx`.

## Inventário (varredura de 2026-09-25 sobre `dev`)

232 FXML (231 com `fx:controller`), 462 arquivos Java importando JavaFX (173 deles em testes).

### Bloqueios que troca de import não resolve

| Uso | Onde | Tratamento previsto |
|---|---|---|
| `com.sun.javafx.scene.control.skin.ScrollPaneSkin` | 32 arquivos | API equivalente no JX ou reescrita pontual |
| `TitledPaneSkin`, `StyleManager`, `LauncherImpl` | 3, 1, 1 arquivos | idem (`Main`, preloader) |
| ControlsFX `Notifications` | 71 arquivos | `Notifications` do JX com a mesma API |
| ControlsFX `CustomTextField` | 1 FXML | controle do JX com o mesmo nome |
| CSS: 8 `.css`, `setStyle("-fx-...")` 406 vezes em 50 arquivos, `styleClass` em 40 FXML | | substituto de CSS que entende `-fx-*` |

### API do JavaFX usada (arquivos que importam)

- Layout: `VBox` 335, `GridPane` 67 (`RowConstraints` 1461 e `ColumnConstraints` 710 no FXML),
  `HBox` 38, `StackPane` 33, `AnchorPane` 11, `BorderPane`, `Region`, `Pane`, `Group`.
- Controles: `TitledPane` 256, `TextArea` 214, `ComboBox` 192, `TextField` 136, `CheckBox` 111,
  `Spinner` 84, `Label` 76, `ScrollPane` 41, `ListView` 39, `Accordion` 34, `TableView` 28,
  `Button` 23, `DatePicker` 23, `RadioButton`/`ToggleGroup` 22, `ProgressIndicator`, `Tooltip`,
  `Tab`/`TabPane`, `PasswordField`, `Pagination`, `Hyperlink`, `Separator`, `ProgressBar`,
  `ButtonBar`, `Text`/`TextFlow`, `ImageView`/`Image`.
- Células: `setCellFactory` 80, `setCellValueFactory` 72, `PropertyValueFactory` 104 ocorrências;
  35 arquivos com `TableCell`/`ListCell` próprias; `TextFormatter`, `StringConverter`, `DateCell`.
- Dados e eventos: `addListener` 1001 ocorrências em 179 arquivos, `FXCollections` 219,
  `ObservableList` 114, `SimpleStringProperty` 54, `ChangeListener`, `ListChangeListener`,
  `EventHandler`, `KeyEvent`/`KeyCode`, `MouseEvent`, arrastar e soltar (`Dragboard` 10).
- Threads: `Platform.runLater` 208 ocorrências em 99 arquivos, `Task` em 22 arquivos.
- Janelas: `Stage` 19, `Alert` 14, `Scene` 10, `Modality`, `FileChooser`, `Dialog`, `Preloader`.
- FXML: `FXMLLoader` chamado direto em 14 arquivos, carregando telas dentro de outras
  (`getChildren().setAll(loader.load())`).
- Classes que estendem JavaFX: `ListCell` 7, `TableCell`, `MultipleSelectionModel`,
  `SpinnerValueFactory`, `StringConverter`, `Application`, `Preloader`.

### Recursos de FXML usados (arquivos)

`<Insets>` 143, `-Infinity` (USE_PREF_SIZE) 123, `@` (location) 45, `styleClass` 40,
`fx:include` 25, `<Font>` 19, `onAction` 19, imagens 7, `stylesheets` 5, `-1.0`
(USE_COMPUTED_SIZE) 3. Sem `fx:define`, expressões, `%resources` ou scripts.

## Consequência para o desenho

As telas se aninham (`FXMLLoader` dentro de controllers colocando o resultado em `getChildren()`),
então não dá para migrar tela a tela com metade em JavaFX e metade nativa sem uma ponte.
A troca de imports acontece de uma vez; o que pode ser gradual é a implementação por trás dos
tipos JX.

## Duas camadas de componentes (decisão de 2026-09-26)

| Camada | Pacote | Nomes | Para quem |
|---|---|---|---|
| Compatibilidade | `com.jxparallel.fx.*` (`jxparallel-fx`) | iguais aos do JavaFX (`Label`, `VBox`, `FXMLLoader`) | projetos JavaFX que migram trocando imports |
| Nativa | `com.jxparallel.ui.*` (`jxparallel-ui`) | prefixo JX (`JXLabel`, `JXButton`, `JXPane`) | projetos novos ou refatorações maiores; comportamento mais completo, pode divergir do JavaFX |

A camada de compatibilidade segue o contrato do JavaFX à risca. Na fase 2b ela passa a ser
implementada sobre os componentes JX nativos (o `Label` compatível usa um `JXLabel` por baixo), para
que as duas camadas não dupliquem código.

## Fase 1: imports trocados, implementação ainda JavaFX (2026-09-26)

Estratégia escolhida: primeiro trocar os imports com as classes JX herdando das do JavaFX (o app
roda igual e dá para medir), depois trocar a implementação por baixo das classes JX pela nativa,
sem voltar a mexer no DeviceConfig.

Peças no JXParallel:

- `jxparallel-fx`: uma classe `com.jxparallel.fx.X` para cada `javafx.X` que o DeviceConfig usa,
  gerada por reflexão: subclasses com os mesmos construtores e `@NamedArg`, subinterfaces, e classes
  de métodos estáticos para `Platform` e `FXCollections` (o gerador da fase 1 foi substituído pelo da
  fase 2 e removido).
- `com.jxparallel.fx.fxml.FXMLLoader`: um ClassLoader que resolve `javafx.X` para
  `com.jxparallel.fx.X` quando ela existe. O `FXMLLoader` do JavaFX repassa o ClassLoader aos
  `fx:include`, então os FXML não mudam.
- Script de imports: troca `import javafx.X;` por `import com.jxparallel.fx.X;` e, nos arquivos com
  `import javafx.pkg.*;`, acrescenta imports JX das classes usadas. Não toca em nenhum outro byte,
  então UTF-8 continua UTF-8 e windows-1252 continua windows-1252.

Mudanças no DeviceConfig (branch `feature/jx-parallel-refactoring`): a dependência no `pom.xml` e
imports em 438 arquivos (`python scripts/migrate-imports.py src test`). Nenhuma outra linha mudou
(`git diff -U0` só tem linhas `import`); nenhum FXML mudou.

Resultados:

| Verificação | Resultado |
|---|---|
| Compilação do código principal (javac 8u51 x86) | 0 erros |
| Compilação dos testes (`mvn test-compile`, sem rodar) | sucesso |
| Imports `javafx.*` trocados por JX | 2582 de 3867 (67%), mais 605 imports JX ao lado de 113 `javafx.pkg.*` |
| 232 FXML carregados, original x migrado (`scripts/FxmlLoadCheck.java`) | resultado idêntico em todos: 229 carregam, as mesmas 3 falham com `NullPointerException` nos dois (falta usuário logado e servidor) |
| Nós criados pelos FXML que já são classes JX | 314 de 512 |

Tudo medido só no JDK 8u51 32 bits, o ambiente do DeviceConfig.

O que continua `javafx.*` e por quê (é exatamente o que a fase 2 precisa resolver):

1. **Tipos que o próprio JavaFX devolve**: `ObservableList` (`getItems()`), `Duration`
   (`Duration.millis()`), `Stage` (`start(Stage)`), `TitledPane` (`Accordion.getPanes()`),
   `MultipleSelectionModel`, `TableRow`, `Dragboard`, `ObservableValue`. Uma classe JX que herda da
   do JavaFX não recebe o objeto do JavaFX.
2. **Classes base**: `Node`, `Parent`, `Region`, `Pane`, `Control`, `Labeled`, `StringProperty`.
   O `Label` do JX herda do `Label` do JavaFX, não de um `Node` do JX, e o Java não tem apelido de
   tipo.
3. **Genéricos das fábricas de células**: `Callback<TableColumn, TableCell>`, `ListView`,
   `ListCell`, `TableColumn`, `TableCell`: o tipo genérico tem que bater exatamente com o do JavaFX.
4. **O que não se estende**: enums (`KeyCode`, `Pos`, `Modality`...), eventos (`KeyEvent`,
   `MouseEvent`...), classes `final` (`Color`, `Font`, `ButtonType`, `FileChooser`), a anotação
   `@FXML`.

Conclusão: dá para trocar tudo, mas não com classes que herdam do JavaFX. Os 4 grupos acima
somem na fase 2, quando as classes JX formarem uma hierarquia própria (o JX devolve os próprios
tipos, tem seus enums, eventos e `@FXML`). Aí a mesma troca de imports cobre 100% dos arquivos.

Achado posterior: 32 controllers leem por reflexão o `viewRect` interno do `ScrollPaneSkin` e fazem
cast para `StackPane`. Na fase 1 isso compila mas daria `ClassCastException` com a tela exibida (o
teste de carga dos FXML não cria skins, então não pega).

## Fase 2: hierarquia JX própria (2026-09-26)

Cada classe `com.jxparallel.fx.X` guarda o objeto JavaFX que a implementa por enquanto (o peer) e
converte tudo que cruza a fronteira em `com.jxparallel.fx.Fx`: nós, listas, enums, eventos,
listeners, lambdas de `java.util.function` e mapas. O JavaFX nunca devolve um tipo seu para a
aplicação, então os quatro grupos da fase 1 deixam de existir.

Peças no JXParallel:

- `scripts/GenerateFxApi.java` (rodado por `scripts/generate-fx-api.sh`): gera 424 classes a partir
  dos pacotes principais do JavaFX 8 (`jxparallel-fx/mirrors/seeds.txt`) e 2 do ControlsFX
  (`jxparallel-fx-controlsfx`). Para cada classe JX criada pela aplicação, o peer é uma subclasse
  JavaFX em `com.jxparallel.fx.peer` que repassa ao objeto JX os métodos que a aplicação sobrescreve
  (os abstratos e `updateItem`, `call`, `succeeded`...), então uma subclasse JX de `ListCell` ou
  `Task` funciona como a do JavaFX. `mirrors/report.txt` lista o gerado e o pulado, com o motivo.
- Escritos à mão (`mirrors/handwritten.txt`): `Application`, `Preloader` (pontes com o launcher do
  JavaFX), `ObservableList`, `ClipboardContent`, `@FXML`, `FXMLLoader`, e os equivalentes das 4
  classes internas usadas (`LauncherImpl`, `StyleManager`, `ScrollPaneSkin` com o campo `viewRect`,
  `TitledPaneSkin`).
- `FXMLLoader` sobre `FxmlTemplate`, uma engine de FXML neutra: reconhece anotações pelo nome
  simples, trata qualquer interface de um método como handler de evento e resolve cada classe
  JavaFX citada no FXML para a do JX. Cada arquivo é interpretado uma vez e fica em cache. Cobre
  `fx:include`, `@` relativo e absoluto, `<URL value>`, listas como atributo (`stylesheets`,
  `styleClass`), `fx:constant` e `@NamedArg` com valores padrão.
- `scripts/migrate-imports.py`: troca o prefixo de qualquer import (simples, com `*` ou estático) e
  de nomes qualificados no código, de `javafx.`, `com.sun.javafx.` e `org.controlsfx.` para
  `com.jxparallel.fx.`, `com.jxparallel.fx.sun.` e `com.jxparallel.fx.controlsfx.`, quando o JX
  tem a classe.

Mudanças no DeviceConfig:

- Frontend (`feature/jx-parallel-refactoring`): imports em 456 arquivos, 3 nomes qualificados
  (`new javafx.scene.image.Image(...)`) e a dependência `jxparallel-fx-controlsfx` no `pom.xml`.
- Backend (`feature/jx-parallel-refactoring`, worktree `DeviceConfigBackend-jx`): 5 classes de
  modelo expõem `ObservableList` e `SimpleStringProperty` do JavaFX na API, e o frontend passa e recebe
  esses objetos. Por decisão do autor, os imports dessas classes (e de 2 testes) também foram
  trocados, mais a dependência `jxparallel-fx` no `pom.xml`. Sem isso, sobravam 5 erros na fronteira.
- Nenhuma linha além de imports, nomes qualificados e `pom.xml`; nenhum FXML.

Resultados (JDK 8u51 32 bits):

| Verificação | Resultado |
|---|---|
| Imports `javafx.*`, `com.sun.javafx.*` e `org.controlsfx.*` que restam (frontend e backend) | 0 |
| Compilação do código principal do frontend | 0 erros |
| Compilação dos testes do frontend (sem rodar) | 0 erros |
| 232 FXML carregados, JavaFX original x JX | resultado idêntico tela a tela: 229 carregam, as mesmas 3 falham com a mesma exceção (falta usuário logado e servidor) |
| Nós criados pelos FXML que são objetos JX | 512 de 512 |
| Testes do `jxparallel-fx` | 5 de 5; com a identidade dono/peer quebrada de propósito, 3 falham |

Problemas encontrados no caminho, cada um corrigido na causa: `fx:id` do `<fx:include>` lido pelo
namespace errado (22 telas sem os painéis incluídos), `<GridPane.margin>` tratado como objeto,
`<Insets top="4"/>` e `<Insets/>` sem os valores padrão de `@NamedArg`, e lambdas de
`UnaryOperator<TextFormatter.Change>` recebendo o `Change` do JavaFX.

Ainda não verificado: abrir o sistema de verdade (precisa de banco, servidor e login) e rodar a
suíte de testes do frontend.

## Fase 2b: desenho nativo (plano, 2026-09-26; implementação concluída em 2026-09-29)

Estado em 2026-09-29: todos os controles da tabela abaixo, o subconjunto de CSS, janelas,
diálogos, notificações, arrastar e soltar, área de transferência, `FileChooser` e as transições
têm implementação nativa, testada sem janela (`-Djx.headless=true`) no `jxparallel-fx` e, no
`jxparallel-ui`, contra o JavaFX 21 (testes diferenciais), com imagens e gravações de referência
e teste de mutação (docs/testing-strategy.md). Também os pontos que a troca de import não
resolvia: `ScrollPaneSkin.viewRect` (lido por reflexão em 32 controllers) e `TitledPaneSkin`
existem no modo nativo, e o `ScrollPane`/`TitledPane` exibido recebe o skin como no JavaFX.

Falta, e depende de acesso: abrir as telas do DeviceConfig no modo nativo (`NativeScreenBatch`,
branch `feature/jx-parallel-refactoring` e banco de homologação) e comparar com o JavaFX tela a
tela. Limitações conhecidas: translate/scale/rotate não são desenhados (só opacidade),
`FillTransition`/`StrokeTransition`/`PathTransition` não existem no nativo, sem `MenuBar`,
`TreeView` e mnemônicos, e um filho que ocupa uma faixa de tamanho fixo num GridPane centralizado
pode ficar 1 px deslocado.


Cobertura medida sobre os 232 FXML (elementos que o `jxparallel-ui` nativo já tem: 11 controles e
os 9 layouts). Hoje só 1 tela usa apenas elementos nativos. Telas cobertas à medida que cada controle
nativo entra, na ordem de maior impacto:

Andamento: Separator, TitledPane e Accordion entraram em 2026-09-28 (layout igual ao JavaFX 21 em
500 árvores aleatórias; num FXML de teste pelo `FXMLLoader` do JX, nenhuma classe sem elemento
nativo e nenhuma API faltando). A cobertura de 81 telas abaixo é a projeção do plano; ainda não foi
remedida sobre os FXML do DeviceConfig. Falta: clicar no título para expandir ou recolher (depende do
despacho de eventos nativo) e `<fx:reference>` no `FXMLLoader` do JX (usado em `expandedPane`).

| Controle nativo adicionado | Telas cobertas | Sem CSS |
|---|---:|---:|
| (hoje) | 1 | 0 |
| Separator, TitledPane, Accordion | 81 | 78 |
| ScrollPane | 105 | 78 |
| Spinner | 128 | 100 |
| RadioButton, Font, Text, ImageView/Image | 148 | 119 |
| ProgressIndicator, TabPane/Tab | 160 | 123 |
| ListView | 186 | 149 |
| TableView/TableColumn | 205 | 168 |
| DatePicker | 227 | 188 |
| Pagination, Hyperlink, TextFlow, CustomTextField, GaussianBlur | 232 | 189 |

Além dos controles: um subconjunto de CSS (43 telas com `styleClass`/`stylesheets`, 448 `setStyle`
no código), janelas e diálogos (41 usos de `Stage`, `Alert`, `Dialog`, `showAndWait`), notificações,
arrastar e soltar, área de transferência e `FileChooser`.

Arquitetura proposta: um único interruptor (`-Djx.backend=native`) escolhe o peer de todas as classes
JX: componente JavaFX (como hoje) ou nó nativo do `jxparallel-ui`. No modo nativo, cada método JX
chama a implementação nativa daquele método; um método ainda sem implementação lança
`UnsupportedOperationException` com o nome, o que transforma "o que falta" numa lista medida. Como o
DeviceConfig tem telas aninhadas numa janela só, ele roda inteiro no nativo só quando tudo que usa
estiver coberto; até lá a validação é tela a tela: cada FXML é carregado no modo nativo, desenhado
pelo Skia/NanoVG e comparado com a imagem do mesmo FXML no JavaFX, com as mesmas medições de tempo e
memória.
