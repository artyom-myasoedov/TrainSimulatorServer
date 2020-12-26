package ru.cs.myasoedov;

import java.io.IOException;

public class Main {
    public static void main(String[] args) {
        try {
            Server server = new Server(8001);
            server.accept();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }
}
