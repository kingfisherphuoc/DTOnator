/*
 * Copyright (C) 2015 Vinaya Prasad N
 *
 *         This program is free software: you can redistribute it and/or modify
 *         it under the terms of the GNU General Public License as published by
 *         the Free Software Foundation, either version 3 of the License, or
 *         (at your option) any later version.
 *
 *         This program is distributed in the hope that it will be useful,
 *         but WITHOUT ANY WARRANTY; without even the implied warranty of
 *         MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *         GNU General Public License for more details.
 *
 *         You should have received a copy of the GNU General Public License
 *         along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nvinayshetty.DTOnator.Ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import nvinayshetty.DTOnator.ActionListener.ContextMenuMouseListener;
import nvinayshetty.DTOnator.ClassCreator.ClassType;
import nvinayshetty.DTOnator.DtoCreationOptions.DtoGenerationFactory;
import nvinayshetty.DTOnator.DtoCreationOptions.FeedType;
import nvinayshetty.DTOnator.DtoCreationOptions.FieldEncapsulationOptions;
import nvinayshetty.DTOnator.DtoCreationOptions.FieldType;
import nvinayshetty.DTOnator.FeedValidator.InputFeedValidationFactory;
import nvinayshetty.DTOnator.NameConventionCommands.CamelCase;
import nvinayshetty.DTOnator.NameConventionCommands.NameParserCommand;
import nvinayshetty.DTOnator.NameConventionCommands.NamePrefixer;
import nvinayshetty.DTOnator.nameConflictResolvers.NameConflictResolverCommand;
import nvinayshetty.DTOnator.nameConflictResolvers.PrefixingConflictResolverCommand;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.event.*;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;

public class InputWindow extends JFrame {
    private PsiClass mClass;
    private Project project;
    private PsiFile mFile;

    private JPanel contentPane;
    private JButton buttonCancel;
    private JButton buttonOk;
    private JTextPane inputFeedText;
    private JLabel exceptionLabel;

    private JRadioButton createSeparateFile;
    private JRadioButton creteSingleFile;
    private JRadioButton makeFieldsPrivate;
    private JRadioButton pojoRadioButton;
    private JRadioButton gsonRadioButton;
    private JRadioButton provideSetter;
    private JRadioButton provideGetter;

    private ButtonGroup classTypeButtonGroup;
    private ButtonGroup feedTypeButtonGroup;
    private JScrollPane exceptionLoggerPane;
    private JRadioButton useCamelCaseRadioButton;
    private JRadioButton prefixEachFieldWithNamingConventionRadioButton;
    private JRadioButton OnCOnflictPrefixFieldNameRadioButton;
    private JTextField onConflictprefixString;
    private JTextField nameConventionPrefix;
    private HashSet<NameConflictResolverCommand> nameConflictResolverCommands;
    private HashSet<NameParserCommand> fieldNameParser = new LinkedHashSet<>();


    public InputWindow(PsiClass mClass) {
        this.mClass = mClass;
        project = mClass.getProject();
        mFile = mClass.getContainingFile();
        setContentPane(contentPane);
        inputFeedText.getRootPane().setSize(750, 400);
        setSize(1000, 600);
        setTitle("Generate DTO");
        getRootPane().setDefaultButton(buttonOk);
        initButtons();
        initListeners();
        setDefaultConditions();
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });
        contentPane.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void setDefaultConditions() {
        exceptionLoggerPane.setVisible(false);
        setEncapsulationOptionsVisible(false);
        gsonRadioButton.setSelected(true);
        creteSingleFile.setSelected(true);
        OnCOnflictPrefixFieldNameRadioButton.setSelected(true);
        onConflictprefixString.setText("m");
    }

    private void initListeners() {
        inputFeedText.addMouseListener(new ContextMenuMouseListener());
        inputFeedText.addKeyListener(new KeyListener() {
            @Override
            public void keyTyped(KeyEvent e) {
            }

            @Override
            public void keyPressed(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ENTER:
                        onOK();
                        break;
                    case KeyEvent.VK_ESCAPE:
                        onCancel();
                        break;
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }
        });
        makeFieldsPrivate.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (makeFieldsPrivate.isSelected()) {
                    setEncapsulationOptionsVisible(true);
                    SetEncapsulationOptionsSelected(true);
                } else {
                    setEncapsulationOptionsVisible(false);
                }
            }
        });
    }

    private void SetEncapsulationOptionsSelected(boolean condition) {
        provideGetter.setSelected(condition);
        provideSetter.setSelected(condition);
    }

    private void setEncapsulationOptionsVisible(boolean condition) {
        provideGetter.setVisible(condition);
        provideSetter.setVisible(condition);
    }

    private void initButtons() {
        buttonOk.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });
        buttonCancel.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });
        classTypeButtonGroup = new ButtonGroup();
        classTypeButtonGroup.add(createSeparateFile);
        classTypeButtonGroup.add(creteSingleFile);
        feedTypeButtonGroup = new ButtonGroup();
        feedTypeButtonGroup.add(pojoRadioButton);
        feedTypeButtonGroup.add(gsonRadioButton);
    }


    private void onOK() {
        final Notification processingNotification = new Notification("DtoGenerator", "Dto generation in Progress", "please wait, it may takes few seconds to generate Dto depending on length of the feed", NotificationType.INFORMATION);
        processingNotification.notify(project);
        InputFeedValidationFactory validator = new InputFeedValidationFactory(getFeedType());
        nameConflictResolverCommands = getNameConflictResolvers();
        HashSet<NameParserCommand> fieldNameParser = getFieldNameParserCommands();
        String text = inputFeedText.getText();
        final boolean isValidFeed = validator.isValidFeed(text, exceptionLoggerPane, exceptionLabel);
        if (isValidFeed) {
            dispose();
            ClassType classType = getClassType();
            EnumSet<FieldEncapsulationOptions> fieldEncapsulationOptions = getFieldEncapsulationOptions();
            PsiFile containingFile = mClass.getContainingFile();
            Object validFeed = validator.getValidFeed();
            JSONObject jsonObject = (JSONObject) validFeed;
            WriteCommandAction writeAction = DtoGenerationFactory.getDtoGeneratorFor(getFeedType(), classType, getFieldTYpe(), fieldEncapsulationOptions, project, containingFile, jsonObject, mClass, nameConflictResolverCommands, fieldNameParser);
            writeAction.execute();
        }
    }

    private HashSet<NameConflictResolverCommand> getNameConflictResolvers() {
        nameConflictResolverCommands = new HashSet<NameConflictResolverCommand>();
        if (OnCOnflictPrefixFieldNameRadioButton.isSelected()) {
            String prefixString = getOnConflictFieldPrefixText();
            NameConflictResolverCommand prefixingConflictResolver = new PrefixingConflictResolverCommand(prefixString);
            updateFieldNameConflictResoverCommands(prefixingConflictResolver);
        }
        return nameConflictResolverCommands;
    }

    private FieldType getFieldTYpe() {
        if (pojoRadioButton.isSelected())
            return FieldType.POJO;
        else
            return FieldType.GSON;
    }

    private FeedType getFeedType() {
        //Todo:implement Xml support
        return FeedType.JSON;
    }

    private String getOnConflictFieldPrefixText() {
        return onConflictprefixString.getText();
    }

    private String getNameConventionPrefixText() {
        return nameConventionPrefix.getText();
    }


    private void updatedFieldNameParserCommands(NameParserCommand parserCommand) {
        fieldNameParser.remove(parserCommand);
        fieldNameParser.add(parserCommand);
    }

    private void updateFieldNameConflictResoverCommands(NameConflictResolverCommand conflictResolver) {
        nameConflictResolverCommands.remove(conflictResolver);
        nameConflictResolverCommands.add(conflictResolver);
    }

    private HashSet<NameParserCommand> getFieldNameParserCommands() {
        NameParserCommand parserCommand;
        if (useCamelCaseRadioButton.isSelected()) {
            parserCommand = new CamelCase();
            updatedFieldNameParserCommands(parserCommand);
        }
        if (prefixEachFieldWithNamingConventionRadioButton.isSelected()) {
            String fieldPrefixText = getNameConventionPrefixText();
            parserCommand = NamePrefixer.prefixWith(fieldPrefixText);
            updatedFieldNameParserCommands(parserCommand);
        }
        return fieldNameParser;
    }

    private EnumSet<FieldEncapsulationOptions> getFieldEncapsulationOptions() {
        EnumSet<FieldEncapsulationOptions> fieldEncapsulationOptions = EnumSet.noneOf(FieldEncapsulationOptions.class);
        if (makeFieldsPrivate.isSelected())
            fieldEncapsulationOptions.add(FieldEncapsulationOptions.PROVIDE_PRIVATE_FIELD);
        if (provideGetter.isSelected())
            fieldEncapsulationOptions.add(FieldEncapsulationOptions.PROVIDE_GETTER);
        if (provideSetter.isSelected())
            fieldEncapsulationOptions.add(FieldEncapsulationOptions.PROVIDE_SETTER);
        return fieldEncapsulationOptions;
    }

    private ClassType getClassType() {
        if (creteSingleFile.isSelected())
            return ClassType.SINGLE_FILE_WITH_INNER_CLASS;
        else
            return ClassType.SEPARATE_FILE;
    }


    private void onCancel() {
        dispose();
    }

}
