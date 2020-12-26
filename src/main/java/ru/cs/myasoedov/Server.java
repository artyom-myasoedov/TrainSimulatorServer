package ru.cs.myasoedov;

import myasoedov.cs.Configs;
import myasoedov.cs.factories.WagonFactory;
import myasoedov.cs.models.storages.Storage;
import myasoedov.cs.models.trains.Train;
import myasoedov.cs.storages.train.FreightTrainDBStorage;
import myasoedov.cs.storages.train.PassengerTrainDBStorage;
import myasoedov.cs.trains.FreightTrain;
import myasoedov.cs.trains.PassengerTrain;
import myasoedov.cs.utils.CommandAndHangar;
import myasoedov.cs.utils.CommandAndTrains;
import myasoedov.cs.utils.DoubleContainer;
import ru.cs.myasoedov.server.interaction.ServerConnection;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    private ServerSocket serverSocket;
    private int port;
    public static Map<Integer, ServerConnection> connections;
    public static Map<Integer, Train> trains;
    public final Integer maxNumberOfClients;
    public final Deque<Integer> queue = new LinkedList<>();
    public final Storage<PassengerTrain> passengerTrainStorage = new PassengerTrainDBStorage(Configs.JDBC_URL, Configs.USER_NAME, Configs.USER_PAROL);
    public final Storage<FreightTrain> freightTrainStorage = new FreightTrainDBStorage(Configs.JDBC_URL, Configs.USER_NAME, Configs.USER_PAROL);

    public Server(int port) throws IOException {
        this.port = port;
        serverSocket = new ServerSocket(port);
        maxNumberOfClients = 5;
        trains = new ConcurrentHashMap<>();
        connections = new ConcurrentHashMap<>();
        for (int i = 0; i < maxNumberOfClients; i++) {
            trains.put(i, new PassengerTrain(UUID.randomUUID()));
            queue.addFirst(i);
        }
    }

    public Integer getMaxNumberOfClients() {
        return maxNumberOfClients;
    }

    public Map<Integer, ServerConnection> getConnections() {
        return connections;
    }

    public void setConnections(Map<Integer, ServerConnection> connections) {
        this.connections = connections;
    }

    public Map<Integer, Train> getTrains() {
        return trains;
    }

    public void setTrains(Map<Integer, Train> trains) {
        this.trains = trains;
    }

    public void accept() throws IOException, ClassNotFoundException {
//        ObjectOutputStream op = new ObjectOutputStream(new FileOutputStream(new File("D:\\test.txt")));
//        ObjectInputStream ip = new ObjectInputStream(new FileInputStream(new File("D:\\test.txt")));
//        String id = UUID.randomUUID().toString();
//        Train train1 = new PassengerTrain(UUID.fromString(id));
//        Train train2;
//        train1.addHeadWagon(WagonFactory.createDefaultRestaurantWagon());
//        train1.addHeadWagon(WagonFactory.createDefaultRestaurantWagon());
//        train1.addHeadWagon(WagonFactory.createDefaultRestaurantWagon());
//        train1.addLocomotive(WagonFactory.createDefaultDieselLocomotive());
//        train1.addLocomotive(WagonFactory.createDefaultDieselLocomotive());
//        System.out.println(train1.getLocomotivesSize() + " " + train1.getWagonsSize() + " " + train1.getClass().getName());
//        System.out.println(train1.getLocomotive(0).getClass().getName());
//        System.out.println(train1.getLocomotive(1).getClass().getName());
//        op.writeObject(train1);
//        op.flush();
//        train2 = (Train) ip.readObject();
//        System.out.println(train2.getLocomotivesSize() + " " + train2.getWagonsSize() + " " + train2.getClass().getName());
//        System.out.println(train2.getLocomotive(0).getClass().getName());
//        System.out.println(train1.getLocomotive(1).getClass().getName());


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

    public void sendExceptionToAll(Throwable e) {
        sendToAll(new DoubleContainer<>("exception", e));
    }

    class MyThread extends Thread{


        private Socket socket;
        public MyThread(Socket socket) {
            this.socket = socket;
        }



        @Override
        public void run() {
            try {
                ServerConnection connection = new ServerConnection(socket);
                connections.put(queue.getFirst(), connection);
                connection.send(new DoubleContainer<>("start", new DoubleContainer<>(queue.pollFirst(), trains)));
                while (true) {
                    try {
                        CommandAndHangar cmh = connection.receive();
                        switch (cmh.getFirst()) {
                            case "update" -> {
                                trains.put(cmh.getSecond().getFirst(), cmh.getSecond().getSecond());
                                sendToAll(new CommandAndTrains("update", trains));
                            }
                            case "save" -> {
                                trains.put(cmh.getSecond().getFirst(), cmh.getSecond().getSecond());
                                if (cmh.getSecond().getSecond().getClass().equals(PassengerTrain.class)) {
                                    passengerTrainStorage.save((PassengerTrain) cmh.getSecond().getSecond());
                                } else {
                                    freightTrainStorage.save((FreightTrain) cmh.getSecond().getSecond());
                                }
                                connections.get(cmh.getSecond().getFirst()).send(new DoubleContainer<>("saved", "Поезд успешно сохранён!"));
                            }
                            case "load" -> {
                                Train train = null;
                                try {
                                    train = passengerTrainStorage.get(cmh.getSecond().getSecond().getId());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                try {
                                    train = freightTrainStorage.get(cmh.getSecond().getSecond().getId());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                                trains.put(cmh.getSecond().getFirst(), train);
                                sendToAll(new DoubleContainer<>("update", trains));
                            }
                            case "disconnect" -> {
                                connections.remove(cmh.getSecond().getFirst());
                                sendToAll(new CommandAndTrains("update", trains));
                            }
                        }
                    } catch (NullPointerException | IOException | SQLException e) {
                        try {
                            connection.send(new DoubleContainer<>("exception", e));
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

}

