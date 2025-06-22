package br.edu.ifal.redes.loadbalancer.server;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList; // Para copiar a lista de nós temporariamente
import java.util.List;

public class HealthChecker extends Thread {

    private static final long CHECK_INTERVAL_MS = 5000; // Verificar a cada 5 segundos

    @Override
    public void run() {
        System.out.println("[INFO] Health Checker iniciado.");
        while (true) {
            try {
                // Criar uma cópia da lista de nós para evitar ConcurrentModificationException
                // caso a lista NODES seja modificada enquanto iteramos.
                // A sincronização é crucial aqui.
                List<ServerNode> currentNodes;
                synchronized (Server.NODES) { // Sincroniza acesso à lista
                    currentNodes = new ArrayList<>(Server.NODES.getList()); // Se sua CircularLinkedList tiver um getList()
                                                                            // ou método similar para acessar a lista interna
                }

                if (currentNodes.isEmpty()) {
                    // System.out.println("[INFO] Nenhum servidor nó para verificar.");
                    Thread.sleep(CHECK_INTERVAL_MS);
                    continue;
                }

                for (ServerNode node : currentNodes) {
                    if (!isNodeHealthy(node)) {
                        System.out.format("[ALERT] Servidor %s:%d está inativo. Removendo do pool.%n",
                                node.getHost(), node.getPort());
                        synchronized (Server.NODES) {
                            Server.NODES.remove(node);
                        }
                    } else {
                        // Opcional: Se o nó foi removido e voltou a ficar saudável, adicione-o de volta.
                        // Isso exigiria uma lista de "nós inativos" ou lógica mais complexa.
                        // Por enquanto, apenas logs.
                        // System.out.format("[INFO] Servidor %s:%d está ativo.%n", node.getHost(), node.getPort());
                    }
                }

                Thread.sleep(CHECK_INTERVAL_MS); // Espera antes da próxima verificação

            } catch (InterruptedException e) {
                System.out.println("[INFO] Health Checker interrompido.");
                Thread.currentThread().interrupt(); // Restaura o status de interrupção
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private boolean isNodeHealthy(ServerNode node) {
        try (Socket healthCheckSocket = new Socket(node.getHost(), node.getPort());
             PrintWriter writer = new PrintWriter(healthCheckSocket.getOutputStream());
             BufferedReader reader = new BufferedReader(new InputStreamReader(healthCheckSocket.getInputStream()))) {

            // Envia uma requisição HTTP simples para o endpoint de health check
            writer.println("GET /health HTTP/1.1");
            writer.println("Host: " + node.getHost());
            writer.println("Connection: close"); // Importante para que o servidor feche a conexão após a resposta
            writer.println(); // Linha vazia para finalizar cabeçalhos HTTP
            writer.flush();

            // LÊ A PRIMEIRA LINHA DA RESPOSTA (Status Line: HTTP/1.1 200 OK)
            String statusLine = reader.readLine();
            // System.out.println("  [DEBUG] Status Line do HealthChecker: " + statusLine); // Opcional para depuração

            if (statusLine == null || !statusLine.contains("200 OK")) {
                // Se a linha de status não existe ou não indica 200 OK, não está saudável
                return false;
            }

            // LÊ OS CABEÇALHOS DA RESPOSTA E DESCARTA
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                // Apenas lemos para descartar, não precisamos processar os cabeçalhos
                // System.out.println("  [DEBUG] Header do HealthChecker: " + headerLine); // Opcional para depuração
            }

            // LÊ O CORPO DA RESPOSTA
            String responseBody = reader.readLine();
            // System.out.println("  [DEBUG] Corpo da Resposta do HealthChecker: " + responseBody); // Opcional para depuração

            // Verifica se o corpo da resposta é "OK"
            return responseBody != null && responseBody.equals("OK");

        } catch (Exception e) {
            System.err.format("[DEBUG] Erro no health check para %s:%d: %s%n", node.getHost(), node.getPort(), e.getMessage()); // Log mais específico
            return false;
        }
    }
}