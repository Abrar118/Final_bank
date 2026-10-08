module com.example.final_bank {
    requires javafx.controls;
    requires javafx.fxml;
    requires de.jensd.fx.glyphs.fontawesome;
    requires java.desktop;


    opens com.example.final_bank to javafx.fxml;
    exports com.example.final_bank;
    exports com.example.final_bank.Controllers;
    exports com.example.final_bank.Controllers.Admin;
    exports com.example.final_bank.Controllers.Client;
    exports com.example.final_bank.Model;
    exports com.example.final_bank.View_manager;
}