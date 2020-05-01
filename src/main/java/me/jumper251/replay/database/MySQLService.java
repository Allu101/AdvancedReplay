package me.jumper251.replay.database;


import me.jumper251.replay.database.utils.DatabaseService;
import me.jumper251.replay.replaysystem.data.ReplayInfo;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class MySQLService extends DatabaseService {

    private static final String NAME = "replays";

    private final MySQLDatabase database;

    public MySQLService(MySQLDatabase database) {
        this.database = database;
    }

    @Override
    public void createReplayTable() {
        database.update("CREATE TABLE IF NOT EXISTS " + NAME + " (id VARCHAR(40) PRIMARY KEY UNIQUE, creator VARCHAR(30), duration INT(255), time BIGINT(255), data LONGBLOB)");
    }

    @Override
    public void addReplay(String id, String creator, int duration, Long time, byte[] data) throws SQLException {
        PreparedStatement statement = database.getConnection().prepareStatement("INSERT INTO " + NAME
                + " (id, creator, duration, time, data) VALUES (?,?,?,?,?)");

        statement.setString(1, id);
        statement.setString(2, creator);
        statement.setInt(3, duration);
        statement.setLong(4, time);
        statement.setBytes(5, data);

        pool.execute(() -> database.update(statement));
    }

    @Override
    public byte[] getReplayData(String id) {
        try(PreparedStatement statement = database.getConnection().prepareStatement("SELECT data FROM " + NAME
                + " WHERE id = ?")) {

            statement.setString(1, id);

            ResultSet resultSet = database.query(statement);

            if(resultSet.next()) {
                return resultSet.getBytes(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    @Override
    public void deleteReplay(String id) {
        try(PreparedStatement statement = database.getConnection().prepareStatement("DELETE FROM " + NAME
                + " WHERE id = ?")) {
            statement.setString(1, id);

            pool.execute(() -> database.update(statement));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean exists(String id) {
        try(PreparedStatement statement = database.getConnection().prepareStatement("SELECT COUNT(1) FROM " + NAME
                + " WHERE id = ?")) {
            statement.setString(1, id);

            ResultSet resultSet = database.query(statement);

            if(resultSet.next()) {
                return resultSet.getInt(1) > 0;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    @Override
    public List<ReplayInfo> getReplays() {
        List<ReplayInfo> replays = new ArrayList<>();

        try(PreparedStatement statement = database.getConnection().prepareStatement("SELECT id,creator,duration,time FROM "
                + NAME)) {
            ResultSet resultSet = database.query(statement);

            while (resultSet.next()) {
                String id = resultSet.getString("id");
                String creator = resultSet.getString("creator");
                int duration = resultSet.getInt("duration");
                long time = resultSet.getLong("time");

                replays.add(new ReplayInfo(id, creator, time, duration));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return replays;
    }

}
