package ru.cs.myasoedov.server.interaction;

import myasoedov.cs.utils.CommandAndHangar;
import myasoedov.cs.utils.DoubleContainer;
import myasoedov.cs.utils.HangarAndTrain;

import java.io.*;
import java.net.Socket;

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
            CommandAndHangar db = (CommandAndHangar) inputStream.readObject();
            return db;
        } catch (ClassCastException | IOException | ClassNotFoundException e) {
            //send(new DoubleContainer<>("exception", new Exception("Сервер не смог принять данные", e)));
            e.printStackTrace();
        }
        return null;
    }

    public void send(DoubleContainer<String, ?> doubleContainer) throws IOException {
        try {
            synchronized (outputStream) {
                outputStream.writeObject(doubleContainer);
                outputStream.flush();
            }
        } catch (IOException e) {
            //send(new DoubleContainer<>("exception", new Exception("Ошибка при передачи данных сервером", e)));
            e.printStackTrace();
        }

    }

    public boolean close(HangarAndTrain hangarAndTrain) throws IOException {
        try {
            CommandAndHangar commandAndHangar = new CommandAndHangar("disconnect", hangarAndTrain);
            outputStream.writeObject(new DoubleContainer<>());
            socket.close();
            return true;
        } catch (IOException e) {
            try {
                throw new IOException("Не получилось закрыть соединение", e);
            } catch (IOException ioException) {
                send(new DoubleContainer<>("exception", new Exception("Не получилось закрыть соединение со стороны сервера!", e)));
            }
            e.printStackTrace();
        }
        return false;
    }
}
