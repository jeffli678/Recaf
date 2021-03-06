package me.coley.recaf;

import java.io.File;
import java.util.Collection;

import org.objectweb.asm.tree.ClassNode;

import javafx.application.Application.Parameters;
import me.coley.event.Bus;
import me.coley.event.Listener;
import me.coley.recaf.bytecode.Agent;
import me.coley.recaf.event.ClassOpenEvent;
import me.coley.recaf.event.NewInputEvent;
import me.coley.recaf.event.UiInitEvent;
import me.coley.recaf.plugin.Launchable;
import me.coley.recaf.plugin.Plugins;
import me.coley.recaf.util.Threads;
import picocli.CommandLine;

/**
 * Initialization handler, does:
 * <ul>
 * <li>Update checking</li>
 * <li>Command line argument parsing</li>
 * <li>Invokes 'Launchable' plugins</li>
 * <li>Initializes 'Input' instance if running in AgentMode</li>
 * </ul>
 * 
 * @author Matt
 */
public class InitListener {
	private String[] launchArgs;

	public InitListener(String[] launchArgs) {
		this.launchArgs = launchArgs;
	}

	@Listener
	private void onInit(UiInitEvent event) {
		try {
			// run update check (if enabled)
			Updater.run(Recaf.args);
			// convert parameters to string array so picocli can parse it
			Parameters paramsFx = event.getLaunchParameters();
			Collection<Launchable> launchables = Plugins.instance().plugins(Launchable.class);
			launchables.forEach(l -> l.preparse(paramsFx));
			LaunchParams params = new LaunchParams();
			CommandLine.call(params, System.out, launchArgs);
			if (Agent.isActive()) {
				NewInputEvent.call(Agent.inst);
			} else {
				// load file & class if specified
				File file = params.initialFile;
				if (file != null && file.exists()) {
					NewInputEvent.call(file);
					Threads.runLaterFx(10, () -> {
						Input in = Input.get();
						String clazz = params.initialClass;
						if (clazz != null && in.classes.contains(clazz)) {
							ClassNode cn = in.getClass(clazz);
							Bus.post(new ClassOpenEvent(cn));
						}
					});
				}
			}
			launchables.forEach(l -> l.postparse(paramsFx));
		} catch (Exception e) {
			Logging.fatal(e);
		}
	}
}
