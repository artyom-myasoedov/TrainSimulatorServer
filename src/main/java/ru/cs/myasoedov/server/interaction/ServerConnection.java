package ru.cs.myasoedov.server.interaction;

import myasoedov.cs.utils.CommandAndHangar;
import myasoedov.cs.utils.DoubleContainer;

import java.io.*;
import java.net.Socket;
import java.util.Objects;

public class ServerConnection {
    private final Socket socket;
    private final ObjectInputStream inputStream;
    private final ObjectOutputStream outputStream;

    public ServerConnection(Socket socket) throws IOException {
        this.socket = socket;
        outputStream = new ObjectOutputStream(socket.getOutputStream());
        inputStream = new ObjectInputStream(socket.getInputStream());
    }

    public CommandAndHangar receive() throws IOException {
        try {
            CommandAndHangar ch = (CommandAndHangar) inputStream.readObject();
            return ch;
        } catch (Exception e) {
            throw new RuntimeException("Не получилось принять данные", e);
        }
    }

    public void send(DoubleContainer<String, ?> doubleContainer) throws IOException {
        try {
            outputStream.writeObject(doubleContainer);
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("Не получилось отправить данные", e);
        }

    }

    public boolean close() {
        try {
            socket.close();
            outputStream.close();
            inputStream.close();
            return true;
        } catch (IOException e) {
            throw new RuntimeException("Не получилось закрыть соединение", e);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServerConnection that = (ServerConnection) o;
        return Objects.equals(socket, that.socket) && Objects.equals(inputStream, that.inputStream) && Objects.equals(outputStream, that.outputStream);
    }

    @Override
    public int hashCode() {
        return Objects.hash(socket, inputStream, outputStream);
    }
}

