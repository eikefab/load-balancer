package br.edu.ifal.redes.loadbalancer.server;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;

public class SocketHandler extends Thread {

    private final Socket client;

    public SocketHandler(Socket client) {
        this.client = client;
    }

    public Socket getClient() {
        return client;
    }

    @Override
    public void start() { 
        try {
            InputStream clientInputStream = client.getInputStream();
            OutputStream clientOutputStream = client.getOutputStream();

            System.out
                .format(
                    "[INFO] Nova conexão recebida: %s:%s",
                    client.getInetAddress().getHostAddress(),
                    client.getPort()
                ).println();

            // 1. Ler a primeira linha (ou um pedaço inicial) para identificar o tipo de requisição.
            // Vamos ler linha por linha para identificar o comando de registro ou a primeira linha HTTP.
            BufferedReader initialReader = new BufferedReader(new InputStreamReader(clientInputStream));
            initialReader.mark(8192); // Marca o stream para poder resetar/reler

            String firstLine = initialReader.readLine();

            if (firstLine != null && firstLine.equals("lb/connect-server")) {
                // É um comando de registro de servidor.
                // As próximas duas linhas contêm host e porta.
                final String host = initialReader.readLine();
                final String port = initialReader.readLine();

                final ServerNode node = new ServerNode(host, Integer.parseInt(port));

                if (!Server.NODES.contains(node)) {
                    Server.NODES.add(node);
                }

                System.out.format("[INFO] Novo servidor adicionado ao pool: %s:%s (Quantidade: %d)%n", host, port, Server.NODES.size());
                PrintWriter writer = new PrintWriter(clientOutputStream);
                writer.printf("[LOAD-BALANCER] Servidor adicionado com sucesso: %s:%s%n", host, port);
                writer.flush();

            } else {
                // É uma requisição de CLIENTE (presumimos HTTP).
                // Agora, precisamos ler o RESTO da requisição HTTP (cabeçalhos e corpo, se houver).
                // Como o `firstLine` já foi lido, precisamos combiná-lo de volta no stream.
                
                ByteArrayOutputStream fullRequestBytes = new ByteArrayOutputStream();
                if (firstLine != null) {
                    fullRequestBytes.write(firstLine.getBytes("UTF-8"));
                    fullRequestBytes.write("\r\n".getBytes("UTF-8")); // Adiciona quebra de linha
                }

                // Lê o restante dos cabeçalhos HTTP até a linha vazia
                String headerLine;
                // IMPORTANTE: initialReader já está lendo do clientInputStream.
                // Continuamos usando initialReader para ler o restante dos cabeçalhos.
                while ((headerLine = initialReader.readLine()) != null && !headerLine.isEmpty()) {
                    fullRequestBytes.write(headerLine.getBytes("UTF-8"));
                    fullRequestBytes.write("\r\n".getBytes("UTF-8"));
                }
                fullRequestBytes.write("\r\n".getBytes("UTF-8")); // Linha vazia final dos cabeçalhos

                // Agora, se houver um Content-Length para POST/PUT, você leria o corpo aqui.
                // Para GET, o fluxo de requisição já terminou.

                // Cria um InputStream a partir dos bytes lidos da requisição completa.
                InputStream requestToForward = new ByteArrayInputStream(fullRequestBytes.toByteArray());


                final ServerNode node = Server.NODES.next(); // Pega o PRÓXIMO nó na lista circular

                if (node == null) {
                    System.out.println("[INFO] Utilizando servidor padrão (nenhum nó disponível).");
                    PrintWriter writer = new PrintWriter(clientOutputStream);
                    sendDefaultResponse(writer);
                    writer.flush();
                } else {
                    System.out.format("[INFO] [FORWARD] Enviando dados para o servidor na porta %d%n", node.getPort());
                    // Chama o novo método forward do ServerNode, passando os streams.
                    node.forward(requestToForward, clientOutputStream);
                }
            }
        } catch (Exception exception) {
            System.err.println("[ERROR] Erro no SocketHandler: " + exception.getMessage());
            exception.printStackTrace(); // Log detalhado para depuração
        } finally {
            try {
                if (client != null && !client.isClosed()) {
                    client.close(); // Garante que o socket do cliente seja fechado
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        start();
    }

    private void sendDefaultResponse(PrintWriter writer) {
        final String response = "Hello from Load Balancer";
        final int length = response.length();

        writer.println("HTTP/1.1 200 OK");
        writer.println("Content-Type: text/plain");
        writer.println("Content-Length: " + length);
        writer.println();
        writer.print(response);

        writer.flush();
    }
}
