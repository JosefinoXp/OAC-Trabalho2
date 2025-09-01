import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Simulador de Máquina Virtual RISC-V Monociclo.
 * Implementa as instruções: add, sub, and, or, addi, lw, sw, beq, bne.
 * Baseado nas especificações do PDF (TRAB2-V1.pdf).
 * Interface ajustada para se aproximar do modelo no PDF, com painéis separados para sinais de controle, registradores, memória, PC, próxima instrução e programa carregado.
 * Memória de instruções separada da memória de dados para evitar sobrescrita (como no datapath monociclo padrão do Patterson & Hennessy).
 */
public class RiscVSimulator extends JFrame {
    // Componentes da GUI
    private JTextArea signalsArea;
    private JTextArea registersArea;
    private JTextArea memoryArea;
    private JTextArea nextInstructionArea;
    private JTextArea programArea;
    private JLabel pcLabel;

    // Estado da máquina
    private int[] registers = new int[32]; // x0 a x31, x0 sempre 0
    private byte[] dmem = new byte[4096]; // Memória de dados byte-addressable (4KB)
    private int[] imem = new int[0]; // Memória de instruções (inicializada vazia para evitar NPE)
    private int pc = 0; // Program Counter (word-aligned, múltiplo de 4)
    private List<String> programLines = new ArrayList<>(); // Linhas do programa para exibição

    // Sinais de controle (ajustados para combinar com o exemplo no PDF)
    private boolean regDst, regWrite, branch, memToReg, memRead, memWrite, aluSource, aluOp1, aluOp0;

    public RiscVSimulator() {
        setTitle("RISC-V Monociclo Simulator");
        setSize(1000, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Painel superior: Botões
        JPanel buttonPanel = new JPanel();
        JButton loadButton = new JButton("Abrir");
        JButton stepButton = new JButton("Step");
        JButton runButton = new JButton("Run");
        buttonPanel.add(loadButton);
        buttonPanel.add(stepButton);
        buttonPanel.add(runButton);
        add(buttonPanel, BorderLayout.NORTH);

        // Painel central: Displays organizados para se aproximar do layout no PDF
        JPanel displayPanel = new JPanel(new GridLayout(2, 3));

        // Sinais de Controle
        signalsArea = new JTextArea();
        signalsArea.setEditable(false);
        displayPanel.add(new JScrollPane(signalsArea));

        // Registradores
        registersArea = new JTextArea();
        registersArea.setEditable(false);
        displayPanel.add(new JScrollPane(registersArea));

        // Memória
        memoryArea = new JTextArea();
        memoryArea.setEditable(false);
        displayPanel.add(new JScrollPane(memoryArea));

        // Programa carregado (como "teste1 - Bloco de Notas")
        programArea = new JTextArea();
        programArea.setEditable(false);
        displayPanel.add(new JScrollPane(programArea));

        // Próxima Instrução
        nextInstructionArea = new JTextArea();
        nextInstructionArea.setEditable(false);
        displayPanel.add(new JScrollPane(nextInstructionArea));

        // PC
        pcLabel = new JLabel("PC: 0");
        displayPanel.add(pcLabel);

        add(displayPanel, BorderLayout.CENTER);

        // Ações dos botões
        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadProgram();
            }
        });

        stepButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executeCycle();
                updateDisplay();
            }
        });

        runButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                while (pc / 4 < imem.length) {
                    executeCycle();
                }
                updateDisplay();
            }
        });

        resetRegisters();
        updateDisplay();
    }

    /**
     * Carrega o programa de um arquivo .txt com instruções binárias de 32 bits.
     */
    private void loadProgram() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().endsWith(".txt");
            }

            @Override
            public String getDescription() {
                return "Arquivos de texto (*.txt)";
            }
        });
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                List<Integer> instructions = new ArrayList<>();
                programLines.clear();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    programLines.add(line);
                    if (line.length() == 32) {
                        int instr = Integer.parseUnsignedInt(line, 2);
                        instructions.add(instr);
                    }
                }
                imem = new int[instructions.size()];
                for (int i = 0; i < instructions.size(); i++) {
                    imem[i] = instructions.get(i);
                }
                pc = 0;
                resetRegisters();
                updateDisplay();
                JOptionPane.showMessageDialog(this, "Programa carregado com " + imem.length + " instruções.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Erro ao carregar arquivo: " + ex.getMessage());
            }
        }
    }

    /**
     * Reseta registradores (x0 = 0, outros 0).
     */
    private void resetRegisters() {
        for (int i = 0; i < 32; i++) {
            registers[i] = 0;
        }
    }

    /**
     * Executa um ciclo monociclo: fetch, decode, execute.
     */
    private void executeCycle() {
        if (pc / 4 >= imem.length) {
            return; // Fim do programa
        }

        // Fetch: Pega instrução da memória de instruções
        int instr = imem[pc / 4];

        // Decode: Extrai campos
        int opcode = instr & 0x7F;
        int rd = (instr >> 7) & 0x1F;
        int funct3 = (instr >> 12) & 0x7;
        int rs1 = (instr >> 15) & 0x1F;
        int rs2 = (instr >> 20) & 0x1F;
        int funct7 = (instr >> 25) & 0x7F;

        // Immediate para I-type (sign extend)
        int immI = instr >> 20;
        if ((immI & 0x800) != 0) immI |= 0xFFFFF000;

        // Immediate para S-type (sign extend)
        int immS = ((instr >> 25) << 5) | ((instr >> 7) & 0x1F);
        if ((immS & 0x800) != 0) immS |= 0xFFFFF000;

        // Immediate para B-type (sign extend, even offset)
        int immB = 0;
        immB |= ((instr >> 31) & 1) << 12; // imm[12]
        immB |= ((instr >> 7) & 1) << 11; // imm[11]
        immB |= ((instr >> 25) & 0x3F) << 5; // imm[10:5]
        immB |= ((instr >> 8) & 0xF) << 1; // imm[4:1]
        // imm[0] = 0
        if ((immB & 0x1000) != 0) immB |= 0xFFFFE000;

        // Reseta sinais de controle
        regDst = false;
        regWrite = false;
        branch = false;
        memToReg = false;
        memRead = false;
        memWrite = false;
        aluSource = false;
        aluOp1 = false;
        aluOp0 = false;

        // Controle baseado em opcode (ajustado para RISC-V monociclo)
        int nextPc = pc + 4;
        int aluResult = 0;
        boolean takeBranch = false;

        switch (opcode) {
            case 0x33: // R-type: add, sub, and, or
                regDst = true;
                regWrite = true;
                aluSource = false;
                aluOp1 = true;
                aluOp0 = false; // 10 para R-type
                int op1 = registers[rs1];
                int op2 = registers[rs2];
                if (funct3 == 0 && funct7 == 0) { // add
                    aluResult = op1 + op2;
                } else if (funct3 == 0 && funct7 == 0x20) { // sub
                    aluResult = op1 - op2;
                } else if (funct3 == 7 && funct7 == 0) { // and
                    aluResult = op1 & op2;
                } else if (funct3 == 6 && funct7 == 0) { // or
                    aluResult = op1 | op2;
                } else {
                    System.out.println("Instrução R-type não suportada: " + Integer.toHexString(instr));
                    return;
                }
                registers[rd] = aluResult;
                break;
            case 0x13: // addi (I-type)
                if (funct3 == 0) {
                    regDst = true;
                    regWrite = true;
                    aluSource = true;
                    aluOp1 = false;
                    aluOp0 = false; // 00 para add
                    aluResult = registers[rs1] + immI;
                    registers[rd] = aluResult;
                } else {
                    System.out.println("Instrução I-type não suportada: " + Integer.toHexString(instr));
                    return;
                }
                break;
            case 0x03: // lw (I-type)
                if (funct3 == 2) { // funct3=010 para lw
                    memRead = true;
                    regWrite = true;
                    memToReg = true;
                    aluSource = true;
                    aluOp1 = false;
                    aluOp0 = false; // 00 para add
                    int address = registers[rs1] + immI;
                    aluResult = readMemory(address);
                    registers[rd] = aluResult;
                } else {
                    System.out.println("Instrução load não suportada: " + Integer.toHexString(instr));
                    return;
                }
                break;
            case 0x23: // sw (S-type)
                if (funct3 == 2) { // funct3=010 para sw
                    memWrite = true;
                    aluSource = true;
                    aluOp1 = false;
                    aluOp0 = false; // 00 para add
                    int address = registers[rs1] + immS;
                    writeMemory(address, registers[rs2]);
                } else {
                    System.out.println("Instrução store não suportada: " + Integer.toHexString(instr));
                    return;
                }
                break;
            case 0x63: // Branches (B-type): beq, bne
                branch = true;
                aluSource = false;
                aluOp1 = false;
                aluOp0 = true; // 01 para branch (sub)
                int diff = registers[rs1] - registers[rs2];
                if (funct3 == 0) { // beq
                    takeBranch = (diff == 0);
                } else if (funct3 == 1) { // bne
                    takeBranch = (diff != 0);
                } else {
                    System.out.println("Instrução branch não suportada: " + Integer.toHexString(instr));
                    return;
                }
                if (takeBranch) {
                    nextPc = pc + immB;
                }
                break;
            default:
                System.out.println("Instrução não suportada: " + Integer.toHexString(instr));
                return;
        }

        // Update PC
        pc = nextPc;

        // x0 sempre 0
        registers[0] = 0;
    }

    /**
     * Lê 32 bits (int) da memória de dados em um endereço (byte-addressable).
     */
    private int readMemory(int address) {
        if (address < 0 || address + 3 >= dmem.length) {
            throw new IllegalArgumentException("Endereço de memória inválido: " + address);
        }
        return (dmem[address] & 0xFF) |
               ((dmem[address + 1] & 0xFF) << 8) |
               ((dmem[address + 2] & 0xFF) << 16) |
               ((dmem[address + 3] & 0xFF) << 24);
    }

    /**
     * Escreve 32 bits (int) na memória de dados em um endereço.
     */
    private void writeMemory(int address, int value) {
        if (address < 0 || address + 3 >= dmem.length) {
            throw new IllegalArgumentException("Endereço de memória inválido: " + address);
        }
        dmem[address] = (byte) (value & 0xFF);
        dmem[address + 1] = (byte) ((value >> 8) & 0xFF);
        dmem[address + 2] = (byte) ((value >> 16) & 0xFF);
        dmem[address + 3] = (byte) ((value >> 24) & 0xFF);
    }

    /**
     * Atualiza a exibição da GUI, seguindo o layout do PDF.
     */
    private void updateDisplay() {
        // Sinais de Controle (lista exata do PDF com valores)
        StringBuilder signals = new StringBuilder("Sinais de Controle:\n");
        signals.append("RegDst: ").append(regDst ? 1 : 0).append("\n");
        signals.append("RegWrite: ").append(regWrite ? 1 : 0).append("\n");
        signals.append("Branch: ").append(branch ? 1 : 0).append("\n");
        signals.append("MemToReg: ").append(memToReg ? 1 : 0).append("\n");
        signals.append("MemRead: ").append(memRead ? 1 : 0).append("\n");
        signals.append("MemWrite: ").append(memWrite ? 1 : 0).append("\n");
        signals.append("ALUSource: ").append(aluSource ? 1 : 0).append("\n");
        signals.append("ALUOP1: ").append(aluOp1 ? 1 : 0).append("\n");
        signals.append("ALUOPO: ").append(aluOp0 ? 1 : 0).append("\n"); // ALUOPO como no PDF (provavelmente ALUOP0)
        signalsArea.setText(signals.toString());

        // Registradores
        StringBuilder regs = new StringBuilder("Registradores:\n");
        for (int i = 0; i < 32; i++) {
            regs.append("x").append(i).append(": ").append(registers[i]).append("\n");
        }
        registersArea.setText(regs.toString());

        // Memória (mostra endereços não-zero ou primeiros 100 bytes)
        StringBuilder mem = new StringBuilder("Memoria:\n");
        boolean hasNonZero = false;
        for (int i = 0; i < dmem.length; i += 4) {
            int val = readMemory(i);
            if (val != 0) {
                mem.append("0x").append(Integer.toHexString(i)).append(": ").append(val).append("\n");
                hasNonZero = true;
            }
        }
        if (!hasNonZero) {
            mem.append("Vazia");
        }
        memoryArea.setText(mem.toString());

        // Programa carregado
        if (programLines.isEmpty()) {
            programArea.setText("Programa:\nNenhum programa carregado");
        } else {
            programArea.setText("Programa:\n" + String.join("\n", programLines));
        }

        // PC
        pcLabel.setText("PC: " + pc);

        // Próxima Instrução (com binary de 32 bits)
        if (pc / 4 < imem.length) {
            int nextInstr = imem[pc / 4];
            String binary = String.format("%32s", Integer.toBinaryString(nextInstr)).replace(' ', '0');
            nextInstructionArea.setText("Proxima Instrucao:\n" + binary);
        } else {
            nextInstructionArea.setText("Proxima Instrucao:\nFim do programa ou nenhum programa carregado");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new RiscVSimulator().setVisible(true));
    }
}
