package convex.gui;

import java.awt.EventQueue;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import convex.api.Convex;
import convex.gui.client.ConvexClient;
import convex.gui.components.AbstractGUI;
import convex.gui.components.ActionPanel;
import convex.gui.components.ConnectPanel;
import convex.gui.dlfs.DLFSBrowser;
import convex.gui.panels.HomePanel;
import convex.gui.peer.PeerGUI;
import convex.gui.tools.HackerTools;
import convex.gui.utils.Toolkit;
import convex.gui.wallet.WalletApp;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class MainGUI extends AbstractGUI {
	public MainGUI() {
		super ("Convex Desktop");
		MigLayout layout=new MigLayout("center");
		setLayout(layout);
		
		add(new HomePanel(),"dock center");
		
		ActionPanel actionPanel=new ActionPanel();
		actionPanel.setLayout(new MigLayout("center,align center,fillx"));
		
		JComponent wallet=createLaunchButton("Wallet",Toolkit.WALLET_ICON,this::launchWallet);
		actionPanel.add(wallet);

		JComponent testNet=createLaunchButton("Peer Manager",Toolkit.TESTNET_ICON,this::launchTestNet);
		actionPanel.add(testNet);
		
		JComponent latticeFS=createLaunchButton("Lattice Filesystem",Toolkit.DLFS_ICON,this::launchDLFS);
		actionPanel.add(latticeFS);

		JComponent terminal=createLaunchButton("Client Terminal",Toolkit.TERMINAL_ICON,this::launchTerminalClient);
		actionPanel.add(terminal);
		
		JComponent hacker=createLaunchButton("Hacker Tools",Toolkit.HACKER_ICON,this::launchTools);
		actionPanel.add(hacker);

		JComponent discord=createLaunchButton("Discord",Toolkit.ECOSYSTEM_ICON,this::launchDiscord);
		actionPanel.add(discord);

		JComponent www=createLaunchButton("convex.world",Toolkit.WWW_ICON,this::launchWebsite);
		actionPanel.add(www);
		
		add(actionPanel,"dock south");
	}
	
	public void launchDLFS() {
		new DLFSBrowser().run();
	}
	
	public void launchWallet() {
	    Convex convex=ConnectPanel.tryConnect(this);
	    if (convex!=null) {
	    	new WalletApp(convex).run();
	    }
	}
	
	
	public void launchTestNet() {
		PeerGUI.runLaunchDialog(this);
	}
	
	public void launchDiscord() {
		Toolkit.launchBrowser("https://discord.com/invite/xfYGq4CT7v");
	}
	
	public void launchTerminalClient() {
	    Convex convex=ConnectPanel.tryConnect(this);
	    if (convex!=null) {
	    	new ConvexClient(convex).run();
	    }
	}
	
	public void launchTools() {
		HackerTools.launch();
	}
	
	public void launchWebsite() {
		Toolkit.launchBrowser("https://convex.world");
	}
	
	public JPanel createLaunchButton(String label, ImageIcon icon, Runnable cmd) {
		JButton butt=new JButton(icon);
		butt.addActionListener(e->{
			EventQueue.invokeLater(cmd);
		});
		
		JLabel lab = new JLabel(label);
		lab.setHorizontalAlignment(SwingConstants.CENTER);
		
		JPanel panel=new JPanel();
		panel.setLayout(new MigLayout());
		panel.add(butt,"dock center");
		panel.add(lab,"dock south");
		return panel;
	}

	/**
	 * Launch the application.
	 * @param args Command line args
	 */
	public static void main(String[] args) {
		Toolkit.init();
		new MainGUI().run();
	}

}
