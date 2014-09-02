package autoeq.eq;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import autoeq.BotUpdateEvent;
import autoeq.EventHandler;
import autoeq.ExternalCommandEvent;
import autoeq.ItemDAO;
import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.ini.Ini2;
import autoeq.modules.pull.MoveModule.Mover;

public interface EverquestSession {
  MySpell getMySpell(SpellData sd);
  CharacterDAO getCharacterDAO();

  void setBotUpdateHandler(EventHandler<BotUpdateEvent> eventHandler);

  void logErr(String string);

  Event<? extends EverquestSession>.Interface onZoned();

  void pulse(Collection<BotUpdateEvent> values);

  <T> void releaseResource(T resource);

  void registerTimer(String string, ExpressionListener expressionListener);

  String translate(String string);

  boolean tryLockMovement();

  void unlockMovement();

  int getZoneId();

  void log(String string);

  void addChatListener(ChatListener chatListener);

  void addUserCommand(String string, Pattern compile, String helpString, UserCommand userCommand);

  boolean delay(int i, Condition earlyExit);
  boolean delay(int i, String string);
  void delay(int i);
  void delayUntilUpdate();

  Me getMe();

  void doCommand(String string);

  void echo(String string);

  boolean evaluate(String string);

  Set<String> getActiveProfiles();

  Set<String> getBotNames();

  Set<Spawn> getBots();

  Set<Spawn> getGroupMembers();

  Ini2 getIni();

  void doActions(List<String> key, Spawn target, Spawn mainTarget, Spawn mainAssist);

  ClickableItem getClickableItem(String name);

  Effect getDebugEffect();

  Effect getEffect(String effectDescription, int aggro);
  Effect getEffect(String effectDescription);

  Set<String> getGroupMemberNames();

  Logger getLogger();

  Ini2 getGlobalIni();

  Spawn getMeSpawn();

  Module getModule(String string);

  int getPort();

  Collection<Effect> getKnownEffects();

  String getMezzables();
  String getUnmezzables();
  String getIgnoreds();
  String getFalseNameds();
  String getTrueNameds();
  String getPriorityTargets();
  String getPullIgnoreds();
  String getValidObjectTargets();

  Spawn getSpawn(int spawnId);

  Spawn getSpawn(String name);

  Set<Spawn> getSpawns();

  SpellData getRawSpellData(int id);

  Spell getSpell(int spellId);

  void addExternalCommand(String command);

  void lockMovement();

  <T> T obtainResource(Class<T> cls);

  void registerExpression(String string, ExpressionListener expressionListener);

  void registerResourceProvider(Class<?> cls, ResourceProvider<?> provider);

  void setCastLockOut(long millis);

  void setCommandHandler(EventHandler<ExternalCommandEvent> eventHandler);

  boolean isProfileActive(List<String> profiles);

  boolean hasInspectBuffs();

  Set<Spawn> getSpawns(double width, double height);

  ItemDAO getItemDAO();

  CastResultMonitor getCastResultMonitor();

  boolean isCombatZone();
  Set<String> getAuras();

  VariableContext getContext();
}
