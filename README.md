# Trabalho 2 - Simulador de RISC-V Monociclo

### Universidade Estadual do Oeste do Paraná - UNIOESTE
### 3° Ano
### Ciências da Computação
### Organização e Algoritmos de Computadores

### Alunos:
#### José Lucas Hoppe Macedo
#### Pedro Gabriel Toscano
#### Hugo Gustavo Cordeiro
#### Matheus Juvêncio do Prado

## Resumo

Implementação de máquina virtual RISC-V monociclo em Java JDK 23. A máquina virtual implementada é capaz de executar apenas os seguintes conjuntos de instruções: 

``add, sub, and, or, addi, lw, sw, beq e bne``

Interface gráfica feita em Java Swing.

## Ferramentas utilizadas

- Visual Studio Code (IDE utilizada)
- Java JDK 23
- Git (Versionamento de código)

## Arquivo testes

Os arquivos teste localizados na pasta "Arquivos Testes" darão uma boa ideia de como funciona a leitura de arquivos do programa.

## Como Executar

Tenha a versão mais recente do Java instalado (Ou JDK 23 acima).

Para executar a aplicação, execute a seguinte linha de comando, dentro da pasta de trabalho:

``javac RiscVSimulator.java``

Após, execute a próxima linha:

``java RiscVSimulator``

### Na interface gráfica:

Clique em "Abrir" para carregar um arquivo de teste.

Use "Step" para executar ciclo a ciclo ou "Run" para rodar até o fim.

## Variáveis implementados

``int[32] registers`` = 32 Registradores padrões RISCV

``byte[4096] dmem`` = Memória de dados

``int[] imem`` = Memória de Instrução

``int pc`` = Contador de Programa

``boolean regDst, regWrite, branch, memToReg, memRead, memWrite, aluSource, aluOp1, aluOp0`` - Sinais de controle

## Métodos implementados

``void loadProgram()`` = Realiza leitura do arquivo com BufferedReader através do JFileChooser e atribui as memórias de instrução para imem;

``void resetRegisters()`` =  Todos os 32 registradores são atribuídos com valor 0;

``void executeCycle()`` = 

- Executa ciclo atual, extrai os campos de Decode, Immediates e Opcode;
- Através de um switch, escolhe Opcode para definir tipo de instrução;
- Com a instrução escolhida, realiza a operação escolhida e guarda resultado da ULA ou escreve na memória a operação;
- Atualiza próximo PC.

``int readMemory(int address)`` = Realiza a leitura da memória de dados de um endereço.

``void writeMemory(int address, int value)`` =  Escreve na memória de dados em um endereço.

``void updateDisplay()`` =  Atualiza os elementos gráficos dentro do JPanel com valores atualizados quando ciclo é executado.
