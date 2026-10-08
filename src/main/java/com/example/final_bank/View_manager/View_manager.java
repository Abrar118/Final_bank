package com.example.final_bank.View_manager;

import com.example.final_bank.HelloApplication;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class View_manager
{
    private final StringProperty customer_option_clicked;
    private final StringProperty admin_option_clicked;
    private boolean super_admin_status;

    public View_manager()
    {
        customer_option_clicked = new SimpleStringProperty("");
        admin_option_clicked = new SimpleStringProperty("");

        try {
            File file = new File(Fpath.data_path + "Data/super_admin_done.txt");
            super_admin_status = !file.createNewFile();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    //client Views,
    public void show_client_view() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/Client/client.fxml"));
        create_stage(fxmlLoader);
    }

    public AnchorPane get_client_dashboard() throws IOException {
        return new FXMLLoader(HelloApplication.class.getResource("fxml/Client/dashboard.fxml")).load();
    }

    public AnchorPane get_customer_profile() throws IOException {
        return new FXMLLoader(HelloApplication.class.getResource("fxml/Client/profile.fxml")).load();
    }

    public AnchorPane get_transaction_view() throws IOException {
        return new FXMLLoader(HelloApplication.class.getResource("fxml/Client/transaction.fxml")).load();
    }

    public AnchorPane get_deposit_view() throws IOException {
        return new FXMLLoader(HelloApplication.class.getResource("fxml/Client/deposit.fxml")).load();
    }

    public void show_customer_care() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/help_center.fxml"));
        create_stage(fxmlLoader);
    }

    public StringProperty get_customer_option_clicked() {
        return customer_option_clicked;
    }
    //client view


    //Admin views
    public StringProperty get_admin_option_clicked() {
        return admin_option_clicked;
    }

    public void show_admin_view() throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/Admin/admin.fxml"));
        create_stage(fxmlLoader);
    }

    public AnchorPane get_clientele_view() throws IOException {
        return new FXMLLoader(HelloApplication.class.getResource("fxml/Admin/clientele.fxml")).load();
    }

    public AnchorPane get_admin_profile() throws IOException {

        return new FXMLLoader(HelloApplication.class.getResource("fxml/Admin/profile.fxml")).load();
    }

    public void show_messages() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/Admin/messages.fxml"));
        create_stage(fxmlLoader);
    }

    public void show_client_details() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/Admin/client-details.fxml"));
        create_stage(fxmlLoader);
    }

    public AnchorPane get_log_in_activity() throws IOException
    {
        return new FXMLLoader(HelloApplication.class.getResource("fxml/Admin/log-in-activity.fxml")).load();
    }

    //admin views

    //general views
    public void show_welcome_screen() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/welcome_window.fxml"));
        create_stage(fxmlLoader);
    }

    public void show_log_in_screen() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/log_in_window.fxml"));
        create_stage(fxmlLoader);
    }

    public boolean get_super_admin_status()
    {
        return super_admin_status;
    }

    public void set_super_admin_status(boolean b)
    {
        super_admin_status = b;
    }

    public void show_converter() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/currency_convert.fxml"));
        create_stage(fxmlLoader);
    }


    public void show_about_us() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/about_us.fxml"));
        create_stage(fxmlLoader);
    }

    public void show_policy() throws IOException
    {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("fxml/policy.fxml"));
        create_stage(fxmlLoader);
    }

    public <type> void create_stage(type fxmlLoader) throws IOException
    {
        Scene scene = null;
        if(fxmlLoader instanceof FXMLLoader) scene = new Scene(((FXMLLoader) fxmlLoader).load());
        else if(fxmlLoader instanceof ScrollPane) scene = new Scene((ScrollPane)fxmlLoader);

        Stage stage=new Stage();
        stage.setTitle("MATBank");
        stage.getIcons().add(new Image(Objects.requireNonNull(HelloApplication.class.getResource("images/logo.png")).toString()));
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public String get_date_time()
    {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        LocalDateTime localDateTime = LocalDateTime.now();
        return localDateTime.format(dateTimeFormatter);
    }

    public void close_stage(Node node)
    {
        Stage stage = (Stage) node.getScene().getWindow();
        stage.close();
    }
}
