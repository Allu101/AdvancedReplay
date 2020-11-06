package me.jumper251.replay.database;

import me.jumper251.replay.ReplaySystem;
import me.jumper251.replay.database.utils.AutoReconnector;
import me.jumper251.replay.database.utils.Database;
import me.jumper251.replay.database.utils.DatabaseService;
import me.jumper251.replay.utils.LogUtils;

import java.sql.*;

public class MySQLDatabase extends Database {

	private Connection connection;
	private MySQLService service;

	public MySQLDatabase(String host, String database, String user, String password) {
		super(host, database, user, password);

		this.service = new MySQLService(this);

		new AutoReconnector(ReplaySystem.instance);
	}

	@Override
	public void connect() {
		try {
			this.connection = DriverManager.getConnection("jdbc:mysql://" + this.host + ":3306/" + this.database
					+ "?useSSL=false", this.user, this.password);

			LogUtils.log("Successfully conntected to database");
		} catch (SQLException e) {
			LogUtils.log("Unable to connect to database: " + e.getMessage());
		}
	}

	@Override
	public void disconnect() {
		if(connection == null) {
			return;
		}

		try {
			this.connection.close();

			LogUtils.log("Connection closed");
		} catch (SQLException e) {
			LogUtils.log("Error while closing the connection: " + e.getMessage());
		}
	}

	@Override
	public DatabaseService getService() {
		return this.service;
	}

	public void update(PreparedStatement statement) {
		try {
			statement.executeUpdate();
			statement.close();
		} catch (SQLException e) {
			connect();

			e.printStackTrace();
		}
	}

	public void update(String qry) {
		try {
			Statement statement = this.connection.createStatement();

			statement.executeUpdate(qry);
			statement.close();
		} catch (SQLException e) {
			connect();

			e.printStackTrace();
		}
	}

	public ResultSet query(PreparedStatement pst) {
		ResultSet resultSet = null;

		try {
			resultSet = pst.executeQuery();
		} catch (SQLException e) {
			connect();

			e.printStackTrace();
		}
		return resultSet;
	}

	public boolean hasConnection() {
		try {
			return this.connection != null && this.connection.isValid(1);
		} catch (SQLException e) {
			return false;
		}
	}

	public String getDatabase() {
		return database;
	}

	public Connection getConnection() {
		return connection;
	}

	public void closeResources(ResultSet resultSet, PreparedStatement statement) {
		if(resultSet != null) {
			try {
				resultSet.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		if(statement != null) {
			try {
				statement.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
}