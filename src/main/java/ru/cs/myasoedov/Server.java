package ru.cs.myasoedov;

import myasoedov.cs.Configs;
import myasoedov.cs.models.storages.Storage;
import myasoedov.cs.models.trains.Train;
import myasoedov.cs.storages.train.FreightTrainDBStorage;
import myasoedov.cs.storages.train.PassengerTrainDBStorage;
import myasoedov.cs.trains.FreightTrain;
import myasoedov.cs.trains.PassengerTrain;
import myasoedov.cs.utils.CommandAndHangar;
import myasoedov.cs.utils.CommandAndTrains;
import myasoedov.cs.utils.DoubleContainer;
import myasoedov.cs.utils.JsonConverter;
import ru.cs.myasoedov.server.interaction.ServerConnection;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;

public class Server {
    private final ServerSocket serverSocket;
    private static Map<Integer, ServerConnection> connections;
    private static Map<Integer, Train> trains;
    private final Integer maxNumberOfClients;
    private final Deque<Integer> queue = new LinkedList<>();
    private final Storage<PassengerTrain> passengerTrainStorage = new PassengerTrainDBStorage(Configs.JDBC_URL, Configs.USER_NAME, Configs.USER_PAROL);
    private final Storage<FreightTrain> freightTrainStorage = new FreightTrainDBStorage(Configs.JDBC_URL, Configs.USER_NAME, Configs.USER_PAROL);
    private final JsonConverter converter = new JsonConverter();

    public Server(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        maxNumberOfClients = 5;
        trains = new HashMap<>();
        connections = new HashMap<>();
        for (int i = 0; i < maxNumberOfClients; i++) {
            trains.put(i, null);
            queue.addLast(i);
        }
    }

    public Integer getMaxNumberOfClients() {
        return maxNumberOfClients;
    }

    public Map<Integer, ServerConnection> getConnections() {
        return connections;
    }

    public void setConnections(Map<Integer, ServerConnection> connections) {
        Server.connections = connections;
    }

    public Map<Integer, Train> getTrains() {
        return trains;
    }

    public void setTrains(Map<Integer, Train> trains) {
        Server.trains = trains;
    }

    public void accept() throws IOException, ClassNotFoundException {

        while (true) {
            Socket socket = serverSocket.accept();
            new MyThread(socket).start();

        }
    }

    public void sendToAll(DoubleContainer<String, ?> dbc) {
        connections.values().forEach(v -> {
            try {
                v.send(dbc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    class MyThread extends Thread {


        private final Socket socket;
        private int exceptionCounter = 0;

        public MyThread(Socket socket) {
            this.socket = socket;
        }


        @Override
        public void run() {
            try {
                ServerConnection connection = new ServerConnection(socket);
                connections.put(queue.getFirst(), connection);
                connection.send(new DoubleContainer<>("start", new DoubleContainer<>(queue.pollFirst(), converter.trainsToJson(trains))));
                while (!this.isInterrupted()) {
                    try {
                        CommandAndHangar cmh = connection.receive();
                        exceptionCounter = 0;
                        switch (cmh.getFirst()) {
                            case "update" -> trains.put(cmh.getSecond().getFirst(), converter.jsonToTrain(cmh.getSecond().getSecond()));
                            case "save" -> {
                                Train train = converter.jsonToTrain(cmh.getSecond().getSecond());
                                trains.put(cmh.getSecond().getFirst(), train);
                                if (train.getClass().equals(PassengerTrain.class)) {
                                    passengerTrainStorage.save((PassengerTrain) train);
                                } else {
                                    freightTrainStorage.save((FreightTrain) train);
                                }
                                connections.get(cmh.getSecond().getFirst()).send(new DoubleContainer<>("saved", "Поезд успешно сохранён!"));
                            }
                            case "load" -> {
                                Train train = converter.jsonToTrain(cmh.getSecond().getSecond());
                                try {
                                    train = passengerTrainStorage.get(train.getId());
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                                try {
                                    train = freightTrainStorage.get(train.getId());
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                }
                                trains.put(cmh.getSecond().getFirst(), train);
                            }
                            case "disconnect" -> disconnectClient(connection);
                        }
                        sendToAll(new DoubleContainer<>("update", converter.trainsToJson(trains)));
                    } catch (Exception e) {
                        if (2 < exceptionCounter++) {
                            disconnectClient(connection);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void disconnectClient(ServerConnection connection) {
            this.interrupt();
            final Integer[] id = new Integer[1];
            connections.forEach((k, v) -> {
                if (v.equals(connection)) {
                    id[0] = k;
                }
            });
            queue.addFirst(id[0]);
            removeConnection(connection);
        }
    }


    public static boolean removeConnection(ServerConnection connection) {
        final Integer[] id = new Integer[1];
        connections.forEach((k, v) -> {
            if (v.equals(connection)) {
                id[0] = k;
            }
        });
        connections.remove(id[0]);
        connection.close();
        trains.put(id[0], null);
        return true;
    }
}

