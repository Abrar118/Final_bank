package com.example.final_bank.Controllers.Admin;

import com.example.final_bank.Model.Model;
import com.example.final_bank.View_manager.Fpath;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.fxml.Initializable;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.paint.ImagePattern;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ResourceBundle;

public class top_bar_controller implements Initializable
{
    public Circle avatar;
    public TextField search_bar;
    public FontAwesomeIconView search_icon;
    public Label name;
    public Text messages;
    public static Text search_text;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle)
    {
        name.setText(Model.get_model().current_admin.getName());
        Model.get_model().current_admin.get_name_property().addListener((observableValue, old_value, new_value) -> name.setText(new_value));

        avatar.setOnMouseClicked(MouseEvent -> profile_pic_change());
        avatar.setFill(new ImagePattern(Model.get_model().current_admin.get_profile_pic()));

        messages.setOnMouseClicked(MouseEvent -> {
        try {Model.get_model().get_view_manager().show_messages();}
        catch (IOException e) {e.printStackTrace();}});

        if(search_text==null) search_text = new Text("");
        search_text.textProperty().bind(search_bar.textProperty());
    }

    private void profile_pic_change()
    {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select an Image");
        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")+"\\Downloads"));
        File file = fileChooser.showOpenDialog(new Stage());

        if(file!=null)
        {
            String path = file.getPath();
            avatar.setFill(new ImagePattern(new Image(path)));

            try
            {
                InputStream in = new FileInputStream(path);
                Path target = Paths.get(Fpath.data_path+"Data/Admin/"+Model.get_model().current_admin.getMail()+"/avatar.png");
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                in.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }
}
