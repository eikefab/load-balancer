package br.edu.ifal.redes.loadbalancer.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

import br.edu.ifal.redes.loadbalancer.utils.CircularLinkedList;

public class Server {

    private static final int PORT = 80;

    public static final CircularLinkedList<ServerNode> NODES = new CircularLinkedList<>();

    private final ServerSocket server;

    public Server() throws IOException {
        this.server = new ServerSocket(PORT);
        new HealthChecker().start();
    }

    public void lockThreadAndStart() {
        System.out.println("[INFO] Load Balancer rodando na porta " + PORT);

        while (true) {
            try (Socket socket = this.server.accept()) {
                final SocketHandler handler = new SocketHandler(socket);

                handler.start();
            } catch (Exception exception) {
                exception.printStackTrace();
            }
        }
    }

    public ServerSocket getServer() {
        return server;
    }

}
