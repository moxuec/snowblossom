package snowblossom.iceleaf;

import java.io.File;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.JButton;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.util.prefs.Preferences;

import snowblossom.node.SnowBlossomNode;
import snowblossom.lib.Globals;
import snowblossom.lib.AddressSpecHash;
import snowblossom.lib.AddressUtil;

import duckutil.PeriodicThread;
import duckutil.ConfigMem;
import java.util.TreeMap;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import snowblossom.lib.NonsenseWordList;
import snowblossom.client.SeedWordList;
import javax.swing.ButtonGroup;
import javax.swing.JRadioButton;
import java.io.PrintStream;
import java.io.FileOutputStream;
import snowblossom.client.SeedUtil;
import snowblossom.client.WalletUtil;

import snowblossom.proto.WalletDatabase;

public class MakeWalletPanel
{
  private JPanel panel;
	protected Preferences ice_leaf_prefs;
  protected IceLeaf ice_leaf;

  protected JTextArea message_box;
  protected JTextField name_field;
  protected JTextField import_field;
  protected JButton random_name_button;
  protected JRadioButton seed_button = new JRadioButton("HD Seed - secp256k1");
  protected JRadioButton old_std_button = new JRadioButton("Old Standard - secp256k1");
  protected JRadioButton qhard_button = new JRadioButton("QHard - secp256k1 + rsa8192 + dstu4145");
  protected JRadioButton import_seed_button = new JRadioButton("Import seed");
  protected JButton make_wallet_button;

  public MakeWalletPanel(IceLeaf ice_leaf)
  {
    this.ice_leaf = ice_leaf;
		ice_leaf_prefs = ice_leaf.getPrefs();
	}

	public void setup()
	{
		GridBagLayout grid_bag = new GridBagLayout();
		panel = new JPanel(grid_bag);

    GridBagConstraints c = new GridBagConstraints();
    c.weightx = 0.0;
    c.weighty= 0.0;
    c.gridheight = 1;
    c.gridwidth = 1;
    c.anchor = GridBagConstraints.NORTHWEST;


    panel.add(new JLabel("Name for new wallet: "), c);
    name_field = new JTextField();
    name_field.setColumns(12);
    panel.add(name_field, c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    random_name_button = new JButton("Random name");
    random_name_button.addActionListener( new RandomButtonAction());
    panel.add(random_name_button, c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(new JLabel("Mode for new wallet: "), c);

    ButtonGroup main_radio_bg = new ButtonGroup();
    main_radio_bg.add(seed_button);
    main_radio_bg.add(old_std_button);
    main_radio_bg.add(qhard_button);
    main_radio_bg.add(import_seed_button);

    seed_button.setSelected(true);

    panel.add(seed_button, c);
    panel.add(old_std_button, c);
    panel.add(qhard_button, c);
    c.gridwidth=1;
    panel.add(import_seed_button, c);
    import_field = new JTextField();
    import_field.setColumns(40);
    c.gridwidth = GridBagConstraints.REMAINDER;
    panel.add(import_field, c);

    make_wallet_button = new JButton("Make wallet");
    make_wallet_button.addActionListener( new MakeWalletAction());
    panel.add(make_wallet_button, c);

    c.gridwidth = GridBagConstraints.REMAINDER;
    c.weightx=1.0;
    c.weighty=1.0;
 
    message_box = new JTextArea();
    message_box.setEditable(false);
    panel.add(message_box,c);

  }

  public JPanel getPanel()
  {
		return panel;
  }

  public void setMessageBox(String text)
  {
    SwingUtilities.invokeLater(new Runnable() {
      public void run()
      {
        message_box.setText(text);
      }
    });
  }
  public class MakeWalletAction implements ActionListener
  {
    public void actionPerformed(ActionEvent e)
    {
      new MakeWalletThread().start();
  
    }
  }

  public class MakeWalletThread extends Thread
  {
    public void run()
    {
      try
      {
        String name = name_field.getText().trim();
        if (name.length()==0)
        {
          throw new Exception("Name must be set");
        }
        setMessageBox("Creating wallet: " + name);

        String wallet_base_path = ice_leaf_prefs.get("wallet_path", null);
        if (wallet_base_path == null) throw new Exception("wallet_path is null");
        File wallet_path = new File(wallet_base_path, name);
        if (wallet_path.exists()) throw new Exception("Already exists: " + wallet_path);
        if (!wallet_path.mkdirs()) throw new Exception("Unable to create: " + wallet_path);

        File wallet_config = new File(wallet_path, "wallet.conf");
        File wallet_db_path = new File(wallet_path, "db");
        PrintStream config_out = new PrintStream(new FileOutputStream(wallet_config));


        TreeMap<String, String> config_map = new TreeMap<>();
        config_map.put("wallet_path", wallet_db_path.getPath());
        config_map.put("network", ice_leaf.getParams().getNetworkName());

        config_out.println("network=" + ice_leaf.getParams().getNetworkName());

        String seed_to_import = null;

        if (seed_button.isSelected())
        {
          config_out.println("key_mode=seed");
          config_map.put("key_mode", "seed");
          seed_to_import = SeedUtil.generateSeed(12);
        }
        else if (old_std_button.isSelected())
        {
          config_out.println("key_mode=standard");
          config_map.put("key_mode", "standard");
        }
        else if (qhard_button.isSelected())
        {
          config_out.println("key_mode=qhard");
          config_map.put("key_mode", "qhard");
          config_map.put("key_count", "1");
        }
        else if (import_seed_button.isSelected())
        {
          config_out.println("key_mode=seed");
          config_map.put("key_mode", "seed");

          seed_to_import = import_field.getText();

        }
        else
        {
          throw new Exception("No mode button selected");
        }
        config_out.close();

        setMessageBox("Generating wallet");

        WalletDatabase db = WalletUtil.makeNewDatabase(new ConfigMem(config_map), ice_leaf.getParams(), seed_to_import);
        WalletUtil.saveWallet(db, wallet_db_path);

        if (seed_button.isSelected())
        {
          setMessageBox(String.format("Wallet %s created with seed:\n%s", name, seed_to_import));
        }
        else
        {
          setMessageBox(String.format("Wallet %s created", name));
        }

        name_field.setText("");
        ice_leaf.getWalletPanel().wake();
        
      }
      catch(Throwable t)
      {
        setMessageBox(t.toString());

      }

    }

  }



  public class RandomButtonAction implements ActionListener
  {
    public void actionPerformed(ActionEvent e)
    {
      StringBuilder sb = new StringBuilder();
      sb.append(NonsenseWordList.getNonsense(1));
      sb.append("-");
      sb.append(SeedWordList.getNonsense(1));
      name_field.setText(sb.toString());
    }
  }
}