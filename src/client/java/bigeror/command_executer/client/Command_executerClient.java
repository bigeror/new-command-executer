package bigeror.command_executer.client;


import java.net.*;
import java.io.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Command_executerClient implements ClientModInitializer {
  public static final Logger logger = LoggerFactory.getLogger("command-executer");
  private static KeyBinding keyBinding;
  private boolean executer_state = false;
  public Socket server = new Socket();
  public BufferedReader reader = null;
  public ExecutorService executorService = Executors.newSingleThreadExecutor();
  public CompletableFuture<String> readBuffer = null;
  public PrintWriter printwriter = null;
  SimpleConfig CONFIG = SimpleConfig.of("command_executer").provider(this::provider).request();

  private void webSocketConnection(MinecraftClient client) throws IOException {
    server = new Socket(IP, PORT);
    printwriter = new PrintWriter(server.getOutputStream());

    executer_state = true;
    client.player.sendMessage(Text.literal("command executer had been enabled"), false);
    client.player.sendMessage(Text.literal("app is listening on IP: " + IP + ", port: " + PORT), false);

    InputStreamReader in = new InputStreamReader(server.getInputStream());
    reader = new BufferedReader(in);

    readBuffer = CompletableFuture.supplyAsync(() -> {
      String line = null;
      try {
        while ((line = reader.readLine()) != null) {
          client.player.networkHandler.sendChatCommand(line);
          logger.info(line);
        }
      } catch (IOException e) {
        logger.error(e.toString());
      }
      return "";
    }, executorService);

    readBuffer.thenAccept(result -> {
      // impossible...
    }).exceptionally(throwable -> {
      return null;
    });
  }

  private String provider(String filename) {
    return "# Config file for command executer made by bigeror\n" + "IP=localhost\n" + "PORT=8000";
  }

  public final String IP = CONFIG.getOrDefault("IP", "localhost");
  public final int PORT = CONFIG.getOrDefault("PORT", 8000);

  @Override
  public void onInitializeClient() {
    keyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding("toggle connection", // The translation key of the keybinding's name
            InputUtil.Type.KEYSYM, // The type of the keybinding, KEYSYM for keyboard, MOUSE for mouse.
            GLFW.GLFW_KEY_R, // The keycode of the key
            "command executer" // The translation key of the keybinding's category.
    ));

    ClientTickEvents.END_CLIENT_TICK.register(client -> {
      while (keyBinding.wasPressed()) {
        if (executer_state) {
          executer_state = false;
          readBuffer.cancel(true);
          client.player.sendMessage(Text.literal("command executer had been disabled"), false);
        } else {
          try {
            webSocketConnection(client);
          } catch (Exception e) {
            logger.error(e.toString());
            client.player.sendMessage(Text.literal(e.toString()), false);
          }
        }
      }
    });

    ClientReceiveMessageEvents.GAME.register((message, error) -> {
      if (!executer_state) return;
      printwriter.println(message.toString());
      printwriter.flush();
    });

    ClientReceiveMessageEvents.CHAT.register((message, SignedMessage, GameProfile, Parameters, Instant) -> {
      if (!executer_state) return;
      printwriter.println(SignedMessage.toString());
      printwriter.flush();
      printwriter.println(SignedMessage.getContent().getLiteralString());
      printwriter.flush();
    });

    logger.info("mod loaded");
  }
}

