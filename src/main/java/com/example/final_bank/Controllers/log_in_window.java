package com.example.final_bank.Controllers;

import com.example.final_bank.Model.Admin;
import com.example.final_bank.Model.Client;
import com.example.final_bank.Model.Model;
import com.example.final_bank.View_manager.Fpath;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ResourceBundle;
import java.util.Scanner;

public class log_in_window implements Initializable
{
    public FontAwesomeIconView facebook;
    public FontAwesomeIconView instagram;
    public FontAwesomeIconView twitter;
    public FontAwesomeIconView contact_admin;
    public TextField username;
    public PasswordField password;
    public PasswordField pin_no;
    public Button sign_in_button;
    public Text switch_admin_user;
    public FontAwesomeIconView switch_admin_user_arrow;
    public ImageView slide_image;
    public AnchorPane social_pane;
    public Text MAT;
    public Text BANK;
    public Text welcome_to;
    public FontAwesomeIconView back_button;
    public Label client_error_message;
    public Label admin_error_message;
    public Text forgot_password;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle)
    {
        switch_admin_user.setOnMouseClicked(MouseEvent->slide_interface());

        back_button.setOnMouseClicked(MouseEvent -> {try {back_to_welcome_screen();}
        catch (IOException e) {throw new RuntimeException(e);}});

        sign_in_button.setOnMouseClicked(MouseEvent -> {try {show_dashboard();}
        catch (IOException e) {e.printStackTrace();}});

        contact_admin.setOnMouseClicked(MouseEvent -> {try{Model.get_model().get_view_manager().show_customer_care();}
        catch (IOException e) {e.printStackTrace();}});

        forgot_password.setOnMouseClicked(MouseEvent -> client_error_message.setText("Ask an admin to reset your password"));

        add_listeners();
    }

    private void add_listeners()
    {
        facebook.setOnMouseClicked(MouseEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.facebook.com"));}
            catch (IOException | URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        });

        instagram.setOnMouseClicked(MouseEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.instagram.com"));}
            catch (IOException | URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        });

        twitter.setOnMouseClicked(MouseEvent -> {
            try {
                Desktop.getDesktop().browse(new URI("https://www.twitter.com"));}
            catch (IOException | URISyntaxException e)
            {
                throw new RuntimeException(e);
            }
        });
    }


    private void show_dashboard() throws IOException
    {
        String email = username.getText();
        String pass = password.getText();
        String pin = pin_no.getText();
        boolean permission = false;
        boolean not_found = false;

        if(switch_admin_user.getText().equals("Sign in as Admin"))
        {
            try
            {
                File file = new File(Fpath.data_path+"Data/Client/"+email+"/name-pass.txt");
                Scanner sc = new Scanner(file);

                sc.nextLine();
                String temp_pass = sc.next();

                if(temp_pass.equals(pass))
                {
                    permission = true;
                    set_logged_in_info("client", file);
                }
                sc.close();
            }
            catch (FileNotFoundException e)
            {
                client_error_message.setText("Account does not exist");
                not_found = true;
            }
        }
        else if(switch_admin_user.getText().equals("Sign in as Client"))
        {
            try
            {
                File file = new File(Fpath.data_path+"Data/Admin/"+email+"/name-pass.txt");
                Scanner sc = new Scanner(file);

                sc.nextLine();
                String temp_pass = sc.next();
                String temp_pin = sc.next();

                if(temp_pass.equals(pass) && temp_pin.equals(pin))
                {
                    permission = true;
                    set_logged_in_info("admin", file);
                }
                sc.close();
            }
            catch (IOException e)
            {
                admin_error_message.setText("Account does not exist");
                not_found =  true;
            }
        }

        if(permission)
        {
            Model.get_model().get_view_manager().close_stage(sign_in_button);

            if(switch_admin_user.getText().equals("Sign in as Admin"))
            {
                write_log_in_history();
                Model.get_model().get_view_manager().show_client_view();
            }
            else if(switch_admin_user.getText().equals("Sign in as Client")) Model.get_model().get_view_manager().show_admin_view();
        }
        else if(!not_found)
        {
            if(switch_admin_user.getText().equals("Sign in as Admin")) client_error_message.setText("Wrong Credentials");
            else if(switch_admin_user.getText().equals("Sign in as Client")) admin_error_message.setText("Wrong Credentials");
        }

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(500),event->{admin_error_message.setText(""); client_error_message.setText("");}));
        timeline.play();
    }

    private void set_logged_in_info(String logg_in_type, File file) throws FileNotFoundException
    {
        Scanner sc = new Scanner(file);
        String mail = sc.next();
        String pass = sc.next();

        if(logg_in_type.equals("client"))
        {
            sc.nextLine();
            String name = sc.nextLine();
            String gender =  sc.next();
            String birthdate = sc.next();
            String facebook = sc.next();
            sc.nextLine();
            String address = sc.nextLine();

            Model.get_model().current_client = new Client(mail,pass, name, gender, birthdate, facebook, address);
            Model.get_model().current_client.set_profile_pic(new Image(Fpath.data_path+"Data/Client/"+mail+"/avatar.png"));

            file = new File(Fpath.data_path+"Data/Client/"+Model.get_model().current_client.getMail()+"/checking.txt");
            sc = new Scanner(file);

            double balance = sc.nextDouble();
            Model.get_model().checking_account.setBalance(balance);

            file = new File(Fpath.data_path+"Data/Client/"+Model.get_model().current_client.getMail()+"/savings.txt");
            sc = new Scanner(file);
            balance = sc.nextDouble();
            Model.get_model().savings_account.setBalance(balance);
        }
        else
        {
            String pin = sc.next();
            sc.nextLine();
            String name = sc.nextLine();
            String gender =  sc.next();
            String birthdate = sc.next();
            String facebook = sc.next();
            sc.nextLine();
            String address = sc.nextLine();

            Model.get_model().current_admin = new Admin(mail, pass, name, gender, birthdate, facebook, address, pin);
            Model.get_model().current_admin.set_profile_pic(new Image(Fpath.data_path+"Data/Admin/"+mail+"/avatar.png"));
        }
        sc.close();
    }

    private void slide_interface()
    {
        username.clear();
        password.clear();

        if(switch_admin_user.getText().equals("Sign in as Admin"))
        {
            TranslateTransition transition = new TranslateTransition(Duration.millis(500), slide_image);
            transition.setToX(620);
            transition.play();

            TranslateTransition transition1 = new TranslateTransition(Duration.millis(1000), welcome_to);
            transition1.setToX(140);
            welcome_to.setStyle("-fx-fill: black");
            transition1.play();

            TranslateTransition transition2 = new TranslateTransition(Duration.millis(1000), BANK);
            transition2.setToX(150);
            BANK.setStyle("-fx-fill: #3f48cc");
            transition2.play();

            TranslateTransition transition3 = new TranslateTransition(Duration.millis(1000), MAT);
            transition3.setToX(140);
            MAT.setStyle("-fx-fill: black");
            transition3.play();

            TranslateTransition transition4 = new TranslateTransition(Duration.millis(100), social_pane);
            transition4.setToX(-1020);
            transition4.play();

            TranslateTransition transition5 = new TranslateTransition(Duration.millis(50), username);
            transition5.setToX(-492);
            transition5.setToY(-50);
            transition5.play();

            TranslateTransition transition6 = new TranslateTransition(Duration.millis(50), password);
            transition6.setToX(-492);
            transition6.setToY(-50);
            transition6.play();

            TranslateTransition transition7 = new TranslateTransition(Duration.millis(50), sign_in_button);
            transition7.setToX(-492);
            transition7.play();

            TranslateTransition transition8 = new TranslateTransition(Duration.millis(500), switch_admin_user);
            transition8.setToX(920);
            transition8.play();

            TranslateTransition transition9 = new TranslateTransition(Duration.millis(500), switch_admin_user_arrow);
            transition9.setToX(1040);
            transition9.play();

            switch_admin_user.setText("Sign in as Client");
            switch_admin_user_arrow.setGlyphName("ARROW_RIGHT");

            contact_admin.setVisible(false);
        }
        else if(switch_admin_user.getText().equals("Sign in as Client"))
        {
            TranslateTransition transition = new TranslateTransition(Duration.millis(500), slide_image);
            transition.setToX(0);
            transition.play();

            TranslateTransition transition1 = new TranslateTransition(Duration.millis(1000), welcome_to);
            transition1.setToX(0);
            welcome_to.setStyle("-fx-fill: white");
            transition1.play();

            TranslateTransition transition2 = new TranslateTransition(Duration.millis(1000), BANK);
            transition2.setToX(0);
            BANK.setStyle("-fx-fill: black");
            transition2.play();

            TranslateTransition transition3 = new TranslateTransition(Duration.millis(1000), MAT);
            transition3.setToX(0);
            MAT.setStyle("-fx-fill: #3f48cc");
            transition3.play();

            TranslateTransition transition4 = new TranslateTransition(Duration.millis(100), social_pane);
            transition4.setToX(0);
            transition4.play();

            TranslateTransition transition5 = new TranslateTransition(Duration.millis(50), username);
            transition5.setToX(0);
            transition5.setToY(0);
            transition5.play();

            TranslateTransition transition6 = new TranslateTransition(Duration.millis(50), password);
            transition6.setToX(0);
            transition6.setToY(0);
            transition6.play();

            TranslateTransition transition7 = new TranslateTransition(Duration.millis(50), sign_in_button);
            transition7.setToX(0);
            transition7.setToY(0);
            transition7.play();

            TranslateTransition transition8 = new TranslateTransition(Duration.millis(500), switch_admin_user);
            transition8.setToX(0);
            transition8.play();

            TranslateTransition transition9 = new TranslateTransition(Duration.millis(500), switch_admin_user_arrow);
            transition9.setToX(0);
            transition9.play();

            switch_admin_user.setText("Sign in as Admin");
            switch_admin_user_arrow.setGlyphName("ARROW_LEFT");

            contact_admin.setVisible(true);
        }
    }

    private void back_to_welcome_screen() throws IOException
    {
        Stage stage = (Stage) back_button.getScene().getWindow();
        stage.close();

        Model.get_model().get_view_manager().show_welcome_screen();
    }

    private void write_log_in_history()
    {
        try
        {
            File file = new File(Fpath.data_path+"Data/Admin/log-in-history.txt");
            file.createNewFile();

            FileWriter cout = new FileWriter(file,true);
            cout.write(username.getText()+"\n");
            cout.write(Model.get_model().get_view_manager().get_date_time()+"\n");
            cout.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
