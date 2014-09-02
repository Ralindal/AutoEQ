package autoeq.test;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import autoeq.BotUpdateEvent;
import autoeq.EventHandler;
import autoeq.ExpressionEvaluator;
import autoeq.ExternalCommandEvent;
import autoeq.ItemDAO;
import autoeq.SpellData;
import autoeq.effects.Effect;
import autoeq.eq.CastResultMonitor;
import autoeq.eq.CharacterDAO;
import autoeq.eq.ChatListener;
import autoeq.eq.ClickableItem;
import autoeq.eq.Condition;
import autoeq.eq.EffectsDAO;
import autoeq.eq.Event.Interface;
import autoeq.eq.EverquestSession;
import autoeq.eq.ExpressionListener;
import autoeq.eq.HistoryValue;
import autoeq.eq.Me;
import autoeq.eq.Module;
import autoeq.eq.MySpell;
import autoeq.eq.Profile;
import autoeq.eq.ProfileExpressionRoot;
import autoeq.eq.ProfileSet;
import autoeq.eq.ResourceProvider;
import autoeq.eq.Spawn;
import autoeq.eq.Spawn.Source;
import autoeq.eq.SpawnType;
import autoeq.eq.Spell;
import autoeq.eq.UserCommand;
import autoeq.eq.VariableContext;
import autoeq.ini.Ini2;
import autoeq.modules.camp.CampModule;
import autoeq.modules.heal.HealModule;
import autoeq.modules.target.TargetModule;

public class ClericTest {
  private static final File PATH = new File("d:/My Games/Everquest/MacroQuest2/Macros");

  private HealModule healModule;

  private Spawn target1;
  private Spawn target2;
  private Spawn target3;
  private Spawn target4;

  private Me me;

  @Before
  public void before() {
    EverquestSession session = new MockEverquestSession();

    CampModule campModule = new CampModule(session);
    TargetModule targetModule = new TargetModule(session, campModule);
    healModule = new HealModule(session, targetModule);

    me = new Me(session, 1);
    me.updateStats(100);
    me.updateMeStats(100000, 100000, 100000, 100000, 100000, 100000);
    me.updateGems(new String[] {
      session.getCharacterDAO().getSpellFromBook("Reverent Light").getId() + ",1,0",
      session.getCharacterDAO().getSpellFromBook("Fifteenth Emblem").getId() + ",1,0",
      session.getCharacterDAO().getSpellFromBook("Reverent Elixir").getId() + ",1,0",
      session.getCharacterDAO().getSpellFromBook("Graceful Remedy").getId() + ",1,0",
      session.getCharacterDAO().getSpellFromBook("Virtuous Intervention").getId() + ",1,0",
      session.getCharacterDAO().getSpellFromBook("Elysian Intervention").getId() + ",1,0",
      session.getCharacterDAO().getSpellFromBook("Word of Reformation").getId() + ",1,0"
    });

    target1 = new Spawn(session, 2);
    target1.updateFixedStats("Warrior", 0, 1, 1, 1, 1, false);
    target1.updateHealth(100, Source.DIRECT);
    target1.updateTTL();

    target2 = new Spawn(session, 3);
    target2.updateFixedStats("Warrior2", 0, 1, 1, 1, 1, false);
    target2.updateHealth(100, Source.DIRECT);
    target2.updateTTL();

    target3 = new Spawn(session, 4);
    target3.updateFixedStats("Warrior3", 0, 1, 1, 1, 1, false);
    target3.updateHealth(100, Source.DIRECT);
    target3.updateTTL();

    target4 = new Spawn(session, 5);
    target4.updateFixedStats("Warrior4", 0, 1, 1, 1, 1, false);
    target4.updateHealth(100, Source.DIRECT);
    target4.updateTTL();


  }

  @Test
  public void should() throws InterruptedException {
    Thread.sleep(1000);
    target1.updateHealth(5, Source.DIRECT);
    target1.updateTTL();
    System.out.println("TOPDPS: " + target1.getTopDPS(7000, 7000));
    System.out.println(healModule.pulse());
  }

  public class MockEverquestSession implements EverquestSession {
    private final Map<Integer, SpellData> rawSpellData;

    {
      rawSpellData = new HashMap<>();
      try(LineNumberReader reader = new LineNumberReader(new FileReader("spells_us.txt"))) {
        String line;

        while((line = reader.readLine()) != null) {
          String[] fields = line.split("\\^");
          SpellData sd = new SpellData(fields);
          rawSpellData.put(sd.getId(), sd);
        }
      }
      catch(Exception e) {
        throw new IllegalStateException(e);
      }
    }

    private final CharacterDAO characterDAO = Mockito.mock(CharacterDAO.class);

    {
      when(characterDAO.getSpellFromBook(anyString())).thenAnswer(new Answer<Spell>() {
        @Override
        public Spell answer(InvocationOnMock invocation) throws Throwable {
          String spellName = (String)invocation.getArguments()[0];

          for(int spellId : rawSpellData.keySet()) {
            if(rawSpellData.get(spellId).getName().equalsIgnoreCase(spellName)) {
              return getSpell(spellId);
            }
          }

          return null;
        }
      });

      when(characterDAO.getAltAbility(anyString())).thenAnswer(new Answer<Spell>() {
        @Override
        public Spell answer(InvocationOnMock invocation) throws Throwable {
          String spellName = (String)invocation.getArguments()[0];

          for(int spellId : rawSpellData.keySet()) {
            if(rawSpellData.get(spellId).getName().equalsIgnoreCase(spellName)) {
              return getSpell(spellId);
            }
          }

          return null;
        }
      });

      when(characterDAO.getSpellSlots()).thenReturn(12);
    }

    private final Map<String, Profile> profiles = new HashMap<>();
    private final Set<ProfileSet> profileSets = new LinkedHashSet<>();

    {
      for(String profileSetNames : getIni().getSection("Modules").getDefault("Profiles", "").split("\\|")) {
        String[] profiles = profileSetNames.split(",");
        ProfileSet profileSet = new ProfileSet(profiles);

        for(String profileName : profiles) {
          String cleanedProfileName = profileName.replaceAll("\\+", "");

          String defaultProfiles = getIni().getSection("Modules").get("Profile." + cleanedProfileName);

          if(defaultProfiles != null) {
            this.profiles.put(cleanedProfileName.toLowerCase(), new Profile(profileSet, cleanedProfileName, defaultProfiles.split(",")));
          }
          else {
            this.profiles.put(cleanedProfileName.toLowerCase(), new Profile(profileSet, cleanedProfileName));
          }
        }

        profileSets.add(profileSet);
      }
    }

    private final EffectsDAO effectsDAO = new EffectsDAO(this, characterDAO);

    @Override
    public void setBotUpdateHandler(EventHandler<BotUpdateEvent> eventHandler) {
      // TODO Auto-generated method stub

    }

    @Override
    public void logErr(String string) {
      // TODO Auto-generated method stub

    }

    @Override
    public Interface onZoned() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void pulse(Collection<BotUpdateEvent> values) {
      // TODO Auto-generated method stub

    }

    @Override
    public <T> void releaseResource(T resource) {
      // TODO Auto-generated method stub

    }

    @Override
    public void registerTimer(String string, ExpressionListener expressionListener) {
      // TODO Auto-generated method stub

    }

    @Override
    public String translate(String string) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public boolean tryLockMovement() {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public void unlockMovement() {
      // TODO Auto-generated method stub

    }

    @Override
    public int getZoneId() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public void log(String string) {
      // TODO Auto-generated method stub

    }

    @Override
    public void addChatListener(ChatListener chatListener) {
      // TODO Auto-generated method stub

    }

    @Override
    public void addUserCommand(String string, Pattern compile, String helpString, UserCommand userCommand) {
      // TODO Auto-generated method stub

    }

    @Override
    public boolean delay(int i, Condition earlyExit) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public boolean delay(int i, String string) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public void delay(int i) {
      // TODO Auto-generated method stub

    }

    @Override
    public void delayUntilUpdate() {
      // TODO Auto-generated method stub

    }

    @Override
    public Me getMe() {
      return me;
    }

    @Override
    public void doCommand(String string) {
      // TODO Auto-generated method stub

    }

    @Override
    public void echo(String string) {
      // TODO Auto-generated method stub

    }

    @Override
    public boolean evaluate(String string) {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public Set<String> getActiveProfiles() {
      return new HashSet<String>() {{
        add("group");
      }};
    }

    @Override
    public Set<String> getBotNames() {
      return new HashSet<>();
    }

    @Override
    public Set<Spawn> getBots() {
      return new HashSet<>();
    }

    @Override
    public Set<Spawn> getGroupMembers() {
      return new HashSet<>(Arrays.asList(target1, target2, target3, target4));
    }

    @Override
    public Ini2 getIni() {
      try {
        return new Ini2(new File(PATH, "jb_cleric.ini"), new File(PATH, "jb_all.ini"));
      }
      catch(IOException e) {
        throw new IllegalStateException(e);
      }
    }

    @Override
    public void doActions(List<String> key, Spawn target, Spawn mainTarget, Spawn mainAssist) {
      // TODO Auto-generated method stub

    }

    @Override
    public ClickableItem getClickableItem(String name) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Effect getDebugEffect() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Effect getEffect(String effectDescription, int aggro) {
      return effectsDAO.getEffect(effectDescription, aggro);
    }

    @Override
    public Set<String> getGroupMemberNames() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Logger getLogger() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Ini2 getGlobalIni() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Spawn getMeSpawn() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Module getModule(String string) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public int getPort() {
      // TODO Auto-generated method stub
      return 0;
    }

    @Override
    public Collection<Effect> getKnownEffects() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public String getMezzables() {
      return "";
    }

    @Override
    public String getUnmezzables() {
      return "";
    }

    @Override
    public String getIgnoreds() {
      return "";
    }

    @Override
    public String getFalseNameds() {
      return "";
    }

    @Override
    public String getTrueNameds() {
      return "";
    }

    @Override
    public String getPriorityTargets() {
      return "";
    }

    @Override
    public String getPullIgnoreds() {
      return "";
    }

    @Override
    public String getValidObjectTargets() {
      return "";
    }

    @Override
    public Spawn getSpawn(int spawnId) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Spawn getSpawn(String name) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Set<Spawn> getSpawns() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public SpellData getRawSpellData(int id) {
      return rawSpellData.get(id);
    }

    private final Map<Integer, Spell> spells = new HashMap<>();

    @Override
    public Spell getSpell(int id) {
      Spell spell = spells.get(id);

      if(spell == null) {
        spell = new Spell(this, id);
        spells.put(id, spell);
      }

      return spell;
    }

    @Override
    public void addExternalCommand(String command) {
      // TODO Auto-generated method stub

    }

    @Override
    public void lockMovement() {
      // TODO Auto-generated method stub

    }

    @Override
    public <T> T obtainResource(Class<T> cls) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public void registerExpression(String string, ExpressionListener expressionListener) {
      // TODO Auto-generated method stub

    }

    @Override
    public void registerResourceProvider(Class<?> cls, ResourceProvider<?> provider) {
      // TODO Auto-generated method stub

    }

    @Override
    public void setCastLockOut(long millis) {
      // TODO Auto-generated method stub

    }

    @Override
    public void setCommandHandler(EventHandler<ExternalCommandEvent> eventHandler) {
      // TODO Auto-generated method stub

    }

    @Override
    public boolean isProfileActive(List<String> profiles) {
      if(profiles == null || profiles.isEmpty() || profileSets.isEmpty()) {
        return true;
      }

      for(int i = 0; i < profiles.size(); i++) {
        String expr = profiles.get(i);

        if(!expr.contains("profile(") && !expr.contains("isActive(") && !expr.contains(".")) {
          profiles.set(i, expr.replaceAll(",", " || ").replaceAll("([A-Za-z][a-zA-Z0-9]*)", "isActive(\"$1\")"));
        }
      }

      return ExpressionEvaluator.evaluate(profiles, new ProfileExpressionRoot(profileSets, this), profiles);
    }

    @Override
    public boolean hasInspectBuffs() {
      // TODO Auto-generated method stub
      return false;
    }

    @Override
    public Set<Spawn> getSpawns(double width, double height) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public ItemDAO getItemDAO() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public CastResultMonitor getCastResultMonitor() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public MySpell getMySpell(SpellData sd) {
      return new MySpell(sd, sd.getClrLevel(), 0, "single", "beneficial", 1500);
    }

    @Override
    public CharacterDAO getCharacterDAO() {
      return characterDAO;
    }

    @Override
    public boolean isCombatZone() {
      return true;
    }

    @Override
    public Set<String> getAuras() {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public Effect getEffect(String effectDescription) {
      // TODO Auto-generated method stub
      return null;
    }

    @Override
    public VariableContext getContext() {
      // TODO Auto-generated method stub
      return null;
    }
  }
}
