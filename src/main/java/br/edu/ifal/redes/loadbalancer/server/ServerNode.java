package br.edu.ifal.redes.loadbalancer.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;

public class ServerNode {

    private final String host;
    private final int port;

    public ServerNode(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true; // Se é a mesma instância de objeto, são iguais
        if (o == null || getClass() != o.getClass()) return false; // Se nulo ou de classe diferente, não são iguais
        ServerNode that = (ServerNode) o; // Converte para ServerNode
        // Compara com base nos valores de host e port
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        // Gera um hashcode baseado nos valores de host e port
        return Objects.hash(host, port);
    }

    public void forward(InputStream clientRequestStream, OutputStream clientResponseStream) {
        try (final Socket socket = new Socket(host, port)) {
            socket.setSoTimeout(5000); // Timeout para a conexão do LB com o nó

            // Copia a requisição do cliente (já lida no SocketHandler) para o servidor nó
            copyStream(clientRequestStream, socket.getOutputStream());

            // Copia a resposta do servidor nó de volta para o cliente
            copyStream(socket.getInputStream(), clientResponseStream);
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("[WARNING] Timeout ao encaminhar para o nó " + host + ":" + port + ". " + e.getMessage());
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }

    // O método copyStream permanece o mesmo
    private void copyStream(InputStream in, OutputStream out) {
        try {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = in.read(buffer)) != -1) {
                out.write(buffer, 0, length);
                out.flush();
            }
        } catch (IOException e) {
            // Isso é comum quando uma conexão é fechada.
            // System.err.println("[DEBUG] IOException durante copyStream: " + e.getMessage());
        }
    }

}
