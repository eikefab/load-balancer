package br.edu.ifal.redes.loadbalancer.node;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class AppServer {

    private static final String LOAD_BALANCER_HOSTNAME = "127.0.0.1";
    private static final int LOAD_BALANCER_PORT = 80;

    private final String hostname;
    private final int port;

    public AppServer(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public String getHostname() {
        return hostname;
    }

    public int getPort() {
        return port;
    }

    public void lockThreadAndStart() {
        connectNode();

        try (final ServerSocket server = new ServerSocket(port)) {
            System.out.println("[INFO] Servidor aberto na porta " + port + ".");

            while (true) {
                // ... dentro do loop while(true)
                try (
                    final Socket socket = server.accept();
                    final PrintWriter writer = new PrintWriter(socket.getOutputStream());
                    final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
                ) {
                    System.out
                        .format(
                            "[INFO] Nova conexão recebida: %s:%s",
                            socket.getInetAddress().getHostAddress(),
                            socket.getPort()
                        ).println();

                    final String requestLine = reader.readLine(); // Lê a primeira linha da requisição

                    // GARANTIR QUE TODOS OS CABEÇALHOS SEJAM LIDOS E DESCARTADOS
                    // O loop `while (reader.ready() ...)` pode não ser suficiente se o cliente fechar a conexão
                    // de forma abrupta ou se a requisição não terminar com uma linha vazia esperada.
                    // Para simplificar, para um GET, basta ler as linhas até uma vazia.
                    // Se `requestLine` for nulo (conexão fechada antes da leitura), tratamos.

                    if (requestLine != null) { // Somente processa se houver uma linha de requisição
                        String headerLine;
                        while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                            // Apenas lê e descarta os cabeçalhos.
                            // Isso consome o resto da requisição HTTP (para um GET simples)
                        }
                    }

                    // LÓGICA DE RESPOSTA
                    if (requestLine != null && requestLine.startsWith("GET /health")) {
                        final String healthResponse = "OK";
                        writer.println("HTTP/1.1 200 OK");
                        writer.println("Content-Type: text/plain");
                        writer.println("Content-Length: " + healthResponse.length());
                        writer.println();
                        writer.println(healthResponse);
                        writer.flush();
                        System.out.println("[INFO] Health check respondido: OK");
                    } else if (requestLine != null) { // Se não for health check, mas é uma requisição válida
                        final String response = "Hello from Server Node at " + port + ".";
                        final int length = response.length();

                        writer.println("HTTP/1.1 200 OK");
                        writer.println("Content-Type: text/plain");
                        writer.println("Content-Length: " + length);
                        writer.println();
                        writer.println(response);
                        writer.flush();
                    }
                    // Se requestLine for null, a conexão foi fechada pelo cliente antes de enviar dados.
                    // O try-with-resources já fechará o socket neste caso.

                } catch (Exception exception) {
                    // Isso pode acontecer se a conexão for resetada ou fechada abruptamente.
                    // Não é necessariamente um erro grave, mas indica que a conexão foi encerrada.
                    System.err.println("[WARNING] Erro durante o processamento da conexão no AppServer: " + exception.getMessage());
                    // exception.printStackTrace(); // Descomente para depuração detalhada, mas pode ser ruidoso
                }
                // ...
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    private void connectNode() {
        System.out.println("[INFO] Tentando conectar ao load balancer...");

        try (final Socket socket = new Socket(LOAD_BALANCER_HOSTNAME, LOAD_BALANCER_PORT);
             final PrintWriter writer = new PrintWriter(socket.getOutputStream());
             final BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            writer.println("lb/connect-server");
            writer.println(hostname);
            writer.println(port);
            writer.flush();

            final String response = reader.readLine();

            System.out.println(response);
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

}
