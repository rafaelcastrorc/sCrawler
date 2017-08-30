package com.rc.crawler;


import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Properties;

class EmailSender extends Dialog<String> {
    private String email = "";
    private ArrayList<String> emails = new ArrayList<>();
    private String password = "";
    private GUILabelManagement guiLabels;
    private boolean emailIsActive = false;

    EmailSender(GUILabelManagement guiLabels) {
        this.guiLabels = guiLabels;
    }


    /**
     * Sends email to the recipients that the user added
     */
    void send(String subject, String content) {
        if (emailIsActive) {
            final String username = email;
            final String password = this.password;

            Properties props = new Properties();
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");

            Session session = Session.getInstance(props,
                    new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(username, password);
                        }
                    });

            Message message = new MimeMessage(session);
            try {
                message.setFrom(new InternetAddress(email));
                Address[] to = new Address[emails.size()];
                int counter = 0;
                for (String recipient : emails) {
                    to[counter] = new InternetAddress(recipient.trim());
                    counter++;
                }
                message.setRecipients(Message.RecipientType.TO, to);
                message.setSubject(subject);
                message.setText(content);
                Transport.send(message);

                emailIsActive = true;

            } catch (MessagingException e) {
                guiLabels.setAlertPopUp(e.getMessage());
                displayPasswordDialog();
                emailIsActive = false;

            }

        }

    }


    /**
     * Display dialog for user to type email, password and recipients
     */
    void displayPasswordDialog() {
        // Create the custom dialog.
        Dialog dialog = new Dialog<>();
        dialog.setTitle("Notifications");
        dialog.setHeaderText("Do you want to receive notifications?");

        // Set the button types.
        ButtonType send = new ButtonType("Send me notifications");
        ButtonType doNotSend = new ButtonType("I don't want to receive notifications");

        dialog.getDialogPane().getButtonTypes().addAll(send, doNotSend);

        // Create the username and password labels and fields.
        VBox vBox = new VBox(20);
        vBox.setAlignment(Pos.CENTER_LEFT);

        //Set the GUI information
        Label instructions = new Label("The program will send you an email:\n" +
                "-Every 6 hours about the current progress of the crawler" +
                "\n-Every time there are 10 blocked proxies that require user intervention (Solve Captcha)." +
                "\n\n" +
                "In order to send emails, the program needs you to provide an email and password to send the emails" +

                ".\nIf Google blocks the program from sending the email, please modify your settings to allow the" +
                " " +
                "crawler to connect.\n      -https://support.google.com/mail/?p=BadCredentials" +
                "\n\nAll your information is private and will be stored temporarily inside the program only while" +
                " it " +
                "is" +
                " open. ");
        Label userEmail = new Label("Email Address:");
        Label password = new Label("Password");

        TextField mailTextField = new TextField();
        mailTextField.setPromptText("Username");
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        //Create the hboxes to hold both fields
        HBox box = new HBox(10);
        box.getChildren().addAll(userEmail, mailTextField);

        HBox box2 = new HBox(10);
        box2.getChildren().addAll(password, passwordField);

        Label emails = new Label("Type the emails that should receive the notifications, separated by ','");
        TextField emailsField = new TextField();
        emailsField.setPromptText("john@gmail.com, joseph@yahoo.com");

        HBox box3 = new HBox(10);
        box3.getChildren().addAll(emails, emailsField);


        vBox.getChildren().addAll(instructions, box, box2, box3);

        buttonsLogic(dialog, send, emailsField, mailTextField, passwordField, vBox);

    }

    /**
     * Configures the function that each button should have
     */
    private void buttonsLogic(Dialog dialog, ButtonType send, TextField emailsField, TextField mailTextField,
                              TextField passwordField, VBox vBox) {

        // Enable/Disable login button depending on whether a username was entered.
        Node loginButton = dialog.getDialogPane().lookupButton(send);
        loginButton.setDisable(true);
        loginButton.addEventFilter(
                ActionEvent.ACTION,
                event -> {
                    // Check whether some conditions are fulfilled
                    if (emailsField.getText().isEmpty() || mailTextField.getText().isEmpty() || passwordField
                            .getText()
                            .isEmpty()) {
                        // The conditions are not fulfilled so we consume the event
                        // to prevent the dialog to close
                        guiLabels.setAlertPopUp("Please fill all fields");
                        event.consume();
                    }
                }
        );

        // Do some validation
        mailTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            loginButton.setDisable(newValue.trim().isEmpty());
        });


        dialog.getDialogPane().setContent(vBox);

        // Request focus on the username field by default.
        Platform.runLater(mailTextField::requestFocus);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.get() == send) {
            email = mailTextField.getText();
            this.password = passwordField.getText();
            if (!emailsField.getText().contains(",")) {
                this.emails.add(emailsField.getText());

            } else {
                for (String s : emailsField.getText().split(",")) {
                    this.emails.add(s.trim());
                }
            }
            verifyConnection();
        }
    }


    /**
     * Verify if user introduced the correct credentials
     */
    private void verifyConnection() {
        final String username = email;
        final String password = this.password;
        Properties props = new Properties();
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        Transport transport;
        try {
            transport = session.getTransport("smtp");
            transport.connect("smtp.gmail.com", username, password);
            transport.close();

            emailIsActive = true;

            //Authentication success
        } catch (MessagingException e) {
            guiLabels.setAlertPopUp("Unable to connect: " + e.getMessage());
            displayPasswordDialog();
            emailIsActive = false;
        }
    }

    /**
     * There is a valid email and password
     *
     * @return boolean
     */
    boolean getIsActive() {
        return emailIsActive;
    }
}
