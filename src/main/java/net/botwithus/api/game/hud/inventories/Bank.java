package net.botwithus.api.game.hud.inventories;

import com.google.common.flogger.FluentLogger;
import net.botwithus.rs3.game.Distance;
import net.botwithus.rs3.game.Item;
import net.botwithus.rs3.game.hud.interfaces.Component;
import net.botwithus.rs3.game.hud.interfaces.Interfaces;
import net.botwithus.rs3.game.minimenu.MiniMenu;
import net.botwithus.rs3.game.minimenu.actions.ComponentAction;
import net.botwithus.rs3.game.minimenu.actions.ObjectAction;
import net.botwithus.rs3.game.queries.builders.characters.NpcQuery;
import net.botwithus.rs3.game.queries.builders.components.ComponentQuery;
import net.botwithus.rs3.game.queries.builders.items.InventoryItemQuery;
import net.botwithus.rs3.game.queries.builders.objects.SceneObjectQuery;
import net.botwithus.rs3.game.vars.VarManager;
import net.botwithus.rs3.script.Execution;
import net.botwithus.rs3.script.ScriptConsole;
import net.botwithus.rs3.util.RandomGenerator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Bank {


    private static final int PRESET_BROWSING_VARBIT_ID = 49662, SELECTED_OPTIONS_TAB_VARBIT_ID = 45191, WITHDRAW_TYPE_VARBIT_ID = 45189, WITHDRAW_X_VARP_ID = 111;

    private static final Inventory BANK = new BankInventory();
    private static final Inventory BACKPACK = new Inventory(93, 517, 15, i -> i);
    private static final FluentLogger log = FluentLogger.forEnclosingClass();

    private static final Pattern BANK_NAME_PATTERN = Pattern.compile("^(?!.*deposit).*(bank|counter).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern BANK_OPTION_PATTERN = Pattern.compile("^.*(Bank|Use).*$");
    private static final String LAST_PRESET_OPTION = "Load Last Preset from";

    private static int previousLoadedPreset = -1;

    private Bank() {

    }

    /**
     * Opens the nearest bank.
     *
     * @return {@code true} if the bank was successfully opened, {@code false} otherwise.
     */
    public static boolean open() {
//        var obj = SceneObjectQuery.newQuery()
//                .name(BANK_NAME_PATTERN)
//                .option(BANK_OPTION_PATTERN).results().nearest();
        var obj = SceneObjectQuery.newQuery().name(BANK_NAME_PATTERN).option("Use")
                .or(SceneObjectQuery.newQuery().name(BANK_NAME_PATTERN).option("Bank"))
                .or(SceneObjectQuery.newQuery().name("Shantay chest")).results().nearest();
        var npc = NpcQuery.newQuery().option("Bank").results().nearest();
        log.atInfo().log("[Bank] Just a test");
        var useObj = true;

        log.atInfo().log("Object is " + (obj != null ? "not null" : "null"));
        log.atInfo().log("Npc is " + (npc != null ? "not null" : "null"));

        if (obj != null && npc != null) {
            log.atInfo().log("Distance.to(obj): " + Distance.to(obj));
            log.atInfo().log("Distance.to(npc): " + Distance.to(npc));
            var objDist = Distance.to(obj);
            var npcDist = Distance.to(npc);
            if (!Double.isNaN(objDist) && !Double.isNaN(npcDist))
                useObj = Distance.to(obj) < Distance.to(npc);
            log.atInfo().log("useObj: " + useObj);
        }
        if (obj != null && useObj) {
            log.atInfo().log("Interacting via Object: " + obj.getName());
            var actions = obj.getOptions();
            log.atInfo().log("Available Options: " + actions);
            if (!actions.isEmpty()) {
                var action = actions.stream().filter(i -> i != null && !i.isEmpty()).findFirst();
                log.atInfo().log("action.isPresent(): " + action.isPresent());
                var result = action.isPresent() && obj.interact(action.get()) && Execution.delayUntil(RandomGenerator.nextInt(3000, 5000), Bank::isOpen);
                log.atInfo().log("Interaction Result1: " + result);
                if (!result) {
                    result = obj.interact(ObjectAction.OBJECT2.getType()) && Execution.delayUntil(RandomGenerator.nextInt(3000, 5000), Bank::isOpen);
                }
                return result;
            } else {
                log.atInfo().log("No options on object");
            }
        } else if (npc != null) {
            var result = npc.interact("Bank") && Execution.delayUntil(RandomGenerator.nextInt(3000, 5000), Bank::isOpen);
            log.atInfo().log("Interacting via Npc: " + result);
            return result;
        }
        return false;
    }

    /**
     * Opens the bank with retry attempts.
     *
     * @param maxAttempts The maximum number of attempts to open the bank.
     * @return {@code true} if the bank was successfully opened, {@code false} otherwise.
     */
    public static boolean openWithRetries(int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (isOpen()) {
                return true;
            }
            if (open()) {
                Execution.delay(1000); // Allow interface to load
                return true;
            }
            Execution.delay(2000); // Retry delay
        }
        return false;
    }

    /**
     * Closes the bank interface with retry attempts.
     *
     * @param maxAttempts The maximum number of attempts to close the bank.
     * @return {@code true} if the bank was successfully closed, {@code false} otherwise.
     */
    public static boolean closeWithRetries(int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (!isOpen()) {
                return true;
            }
            if (close()) {
                return true;
            }
            Execution.delay(2000); // Retry delay
        }
        return false;
    }

    /**
     * Checks if an item exists in the backpack or the bank.
     *
     * @param itemName The name of the item to search for.
     * @return {@code true} if the item is found, {@code false} otherwise.
     */
    public static boolean containsItem(String itemName) {
        boolean inBackpack = Backpack.getItems().stream()
                .anyMatch(item -> itemName.equalsIgnoreCase(item.getName()));
        if (inBackpack) {
            return true;
        }
        if (isOpen()) {
            return Arrays.stream(getItems())
                    .anyMatch(item -> itemName.equalsIgnoreCase(item.getName()));
        }
        return false;
    }


    /**
     * Checks if the bank is open
     *
     * @return true if the bank is open, false otherwise
     */
    public static boolean isOpen() {
        return Interfaces.isOpen(517);
    }

    /**
     * Closes the bank interface.
     *
     * @return true if the interface was closed, false otherwise
     */
    public static boolean close() {
//        return Interface.find(ComponentQuery.newQuery(517)
//                .option("Close"))
//                .first()
//                .map(Component::doAction)
//                .orElse(false);
        // TODO: Update to no longer use MiniMenu.doAction
        return MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1, -1, 33882430);
    }

    public static boolean loadLastPreset() {
        var obj = SceneObjectQuery.newQuery()
                .option(LAST_PRESET_OPTION).results().nearest();
        var npc = NpcQuery.newQuery().option(LAST_PRESET_OPTION).results().nearest();
        var useObj = true;

        log.atInfo().log("Object is " + (obj != null ? "not null" : "null"));
        log.atInfo().log("Npc is " + (npc != null ? "not null" : "null"));

        if (obj != null && npc != null) {
            log.atInfo().log("Distance.to(obj): " + Distance.to(obj));
            log.atInfo().log("Distance.to(npc): " + Distance.to(npc));
            var objDist = Distance.to(obj);
            var npcDist = Distance.to(npc);
            if (!Double.isNaN(objDist) && !Double.isNaN(npcDist))
                useObj = Distance.to(obj) < Distance.to(npc);
            log.atInfo().log("useObj: " + useObj);
        }
        if (obj != null && useObj) {
            log.atInfo().log("Interacting via Object: " + obj.getName());
            return obj.interact(LAST_PRESET_OPTION);
        } else if (npc != null) {
            log.atInfo().log("Interacting via Npc: " + npc.getName());
            return npc.interact(LAST_PRESET_OPTION);
        }
        return false;
    }

    /**
     * Gets all the items in the players bank
     *
     * @return returns an array containing all items in the bank.
     */
    public static Item[] getItems() {
        return InventoryItemQuery.newQuery(95).results().stream().filter(i -> i.getId() != -1).toArray(Item[]::new);
    }


    /**
     * Gets the count of a specific item in the bank.
     *
     * @param query the predicate specifying the item to count.
     * @return returns an integer representing the count of the item
     */
    public static int count(InventoryItemQuery query) {
        return query.results().stream().mapToInt(Item::getStackSize).sum();
    }

    /**
     * Gets the first item matching the predicate.
     *
     * @param query the predicate specifying the item to count.
     * @return returns the item, or null if not found.
     */
    public static Item first(InventoryItemQuery query) {
        return query.results().first();
    }

    /**
     * Determines if the bank is empty
     *
     * @return returns true if empty, false if not.
     */
    public static boolean isEmpty() {
        return getItems().length == 0;
    }

    /**
     * Determines if the bank contains an item.
     *
     * @param query the predicate specifying the item to count.
     * @return returns the item, or null if not found.
     */
    public static boolean contains(InventoryItemQuery query) {
        return count(query) > 0;
    }

    public static boolean contains(String... itemNames) {
        return !InventoryItemQuery.newQuery(95).name(itemNames).results().isEmpty();
    }

    public static boolean contains(Pattern itemNamePattern) {
        return !InventoryItemQuery.newQuery(95).name(itemNamePattern).results().isEmpty();
    }

    public static int getCount(String... itemNames) {
        return count(InventoryItemQuery.newQuery(95).name(itemNames));
    }

    public static int getCount(Pattern namePattern) {
        return count(InventoryItemQuery.newQuery(95).name(namePattern));
    }

    /**
     * Withdraws an item from the bank
     *
     * @param query  the query specifying the item to withdraw.
     * @param option the doAction option to execute on the item.
     */
    public static boolean withdraw(InventoryItemQuery query, int option) {
        setTransferOption(TransferOptionType.ALL);
        Item item = query.results().first();
        if (item != null) {
            ScriptConsole.println("Item: " + item);
        } else {
            ScriptConsole.println("Item is null");
        }
        return item != null && BANK.interact(item.getSlot(), option);
    }

    /**
     * Withdraws an item from the inventory.
     *
     * @param itemName The name of the item to withdraw.
     * @param option   The option to withdraw.
     * @return True if the item was successfully withdrawn, false otherwise.
     */
    public static boolean withdraw(String itemName, int option) {
        if (itemName != null && !itemName.isEmpty()) {
            return withdraw(InventoryItemQuery.newQuery().inventories(95).name(itemName), option);
        }
        return false;
    }

    /**
     * Withdraws an item from the inventory.
     *
     * @param itemId The ID of the item to withdraw.
     * @param option The option of the item to withdraw.
     * @return True if the item was successfully withdrawn, false otherwise.
     */
    public static boolean withdraw(int itemId, int option) {
        if (itemId >= 0) {
            return withdraw(InventoryItemQuery.newQuery().inventories(95).ids(itemId), option);
        }
        return false;
    }

    /**
     * Withdraws an item from the inventory.
     *
     * @param pattern The pattern of the item to withdraw.
     * @param option  The option of the item to withdraw.
     * @return true if the item was successfully withdrawn, false otherwise.
     */
    public static boolean withdraw(Pattern pattern, int option) {
        if (pattern != null) {
            return withdraw(InventoryItemQuery.newQuery().inventories(95).name(pattern), option);
        }
        return false;
    }

    /**
     * Withdraws all of a given item from the inventory.
     *
     * @param name The name of the item to withdraw.
     * @return true if the item was successfully withdrawn, false otherwise.
     */
    public static boolean withdrawAll(String name) {
        return withdraw(InventoryItemQuery.newQuery().inventories(95).name(name), 1);
    }

    public static boolean withdrawAll(int id) {
        return withdraw(InventoryItemQuery.newQuery().inventories(95).ids(id), 1);
    }

    public static boolean withdrawAll(Pattern pattern) {
        return withdraw(InventoryItemQuery.newQuery().inventories(95).name(pattern), 1);
    }

    /**
     * Deposits all items in the player's bank.
     *
     * @return true if the items were successfully deposited, false otherwise
     */
    public static boolean depositAll() {
        setTransferOption(TransferOptionType.ALL);
        var comp = ComponentQuery.newQuery(517).option("Deposit carried items").results().first();
        return comp != null && comp.interact(1);
    }

    /**
     * Attempts to deposit an item from the given {@link InventoryItemQuery}.
     *
     * @param query  The query to use for finding the item to deposit.
     * @param option The option to use when depositing the item.
     * @return {@code true} if the item was successfully deposited, {@code false} otherwise.
     */
    public static boolean deposit(ComponentQuery query, int option) {
        var item = query.results().first();
        return deposit(item, option);
    }

    public static boolean depositAll(ComponentQuery query) {
        var item = query.results().first();
        return deposit(item, 1);//item.getOptions().contains("Deposit-All") ? 7 : 1);
    }

    public static boolean deposit(Component comp, int option) {
        setTransferOption(TransferOptionType.ALL);
        return comp != null && comp.interact(option) && Execution.delay(RandomGenerator.nextInt(400, 700));
    }

    public static boolean depositAll(String... itemNames) {
        return !InventoryItemQuery.newQuery(93).name(itemNames).results().stream().map(Item::getId).distinct().map(
                i -> depositAll(ComponentQuery.newQuery(517).item(i))
        ).toList().contains(false);
    }

    public static boolean depositAll(int... itemIds) {
        return !InventoryItemQuery.newQuery(93).ids(itemIds).results().stream().map(Item::getId).distinct().map(
                i -> depositAll(ComponentQuery.newQuery(517).item(i))
        ).toList().contains(false);
    }

    public static boolean depositAll(Pattern... patterns) {
        return !InventoryItemQuery.newQuery(93).name(patterns).results().stream().map(Item::getId).distinct().map(
                i -> depositAll(ComponentQuery.newQuery(517).item(i))
        ).toList().contains(false);
    }

    public static boolean depositAllExcept(String... itemNames) {
        var nameSet = new HashSet<>(Arrays.asList(itemNames));
        var idMap = Backpack.getItems().stream().filter(i -> Arrays.stream(itemNames).toList().contains(i)).collect(Collectors.toMap(Item::getId, Item::getName));
        var items = ComponentQuery.newQuery(517).results().stream().filter(
                i -> !nameSet.contains(idMap.get(i.getItemId())) && (i.getOptions().contains("Deposit-All") || i.getOptions().contains("Deposit-1")))
                .map(Component::getItemId)
                .collect(Collectors.toSet());
        return !items.stream().map(i -> depositAll(ComponentQuery.newQuery(517).item(i))).toList().contains(false);
    }

    public static boolean depositAllExcept(int... ids) {
        var idSet = Arrays.stream(ids).boxed().collect(Collectors.toSet());
        var items = ComponentQuery.newQuery(517).results().stream().filter(
                        i -> !idSet.contains(i.getItemId()) && (i.getOptions().contains("Deposit-All") || i.getOptions().contains("Deposit-1")))
                .map(Component::getItemId)
                .collect(Collectors.toSet());
        return !items.stream().map(i -> depositAll(ComponentQuery.newQuery(517).item(i))).toList().contains(false);
    }

    public static boolean depositAllExcept(Pattern... patterns) {
        var idMap = Backpack.getItems().stream().filter(i -> i.getName() != null && Arrays.stream(patterns).map(p -> p.matcher(i.getName()).matches()).toList().contains(true))
                .collect(Collectors.toMap(Item::getId, Item::getName));
        var items = ComponentQuery.newQuery(517).results().stream().filter(
                        i -> !idMap.containsKey(i.getItemId()) && (i.getOptions().contains("Deposit-All") || i.getOptions().contains("Deposit-1")))
                .map(Component::getItemId)
                .collect(Collectors.toSet());
        return !items.stream().map(i -> depositAll(ComponentQuery.newQuery(517).item(i))).toList().contains(false);
    }

    /**
     * Deposits an item into the inventory.
     *
     * @param itemId The ID of the item to deposit.
     * @param option The option to use when depositing the item.
     * @return True if the item was successfully deposited, false otherwise.
     */
    public static boolean deposit(int itemId, int option) {
        return deposit(ComponentQuery.newQuery(517).item(itemId), option);
    }

    /**
     * Deposits an item into the inventory.
     *
     * @param name   The name of the item to deposit.
     * @param spred  The spread function to use when searching for the item.
     * @param option The option to use when depositing the item.
     * @return True if the item was successfully deposited, false otherwise.
     */
    public static boolean deposit(String name, BiFunction<String, CharSequence, Boolean> spred, int option) {
        return deposit(ComponentQuery.newQuery(517).itemName(name, spred), option);
    }

    /**
     * Deposits an amount of money into an account.
     *
     * @param name   The name of the account to deposit into.
     * @param option The amount of money to deposit.
     * @return True if the deposit was successful, false otherwise.
     */
    public static boolean deposit(String name, int option) {
        return deposit(name, String::contentEquals, option);
    }

    /**
     * Deposits all items of the specified name into the bank with retries.
     *
     * @param itemName The name of the item to deposit.
     * @param maxAttempts The maximum number of attempts.
     * @return {@code true} if the deposit was successful, {@code false} otherwise.
     */
    public static boolean depositAllWithRetries(String itemName, int maxAttempts) {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (depositAll(itemName)) {
                Execution.delay(1000); // Allow bank interface to process
                return true;
            }
            Execution.delay(2000); // Retry delay
        }
        return false;
    }


    /**
     * Loads the given preset number.
     *
     * @param presetNumber the preset number to load
     * @return true if the preset was successfully loaded, false otherwise
     * @throws InterruptedException if the thread is interrupted while sleeping
     */
    // TODO: Update to no longer use MiniMenu.doAction
    public static boolean loadPreset(int presetNumber) {
        int presetBrowsingValue = VarManager.getVarbitValue(PRESET_BROWSING_VARBIT_ID);
        if ((presetNumber >= 10 && presetBrowsingValue < 1) || (presetNumber < 10 && presetBrowsingValue > 0)) {
            MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1, 100, 33882231);
            Execution.delay(RandomGenerator.nextInt(300, 700));
        }
        var result = MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1, presetNumber % 9,
                                 33882231);//presetComp != null && presetComp.interact("Load");
        if (result) {
            previousLoadedPreset = presetNumber;
        }
        return result;
    }

    /**
     * Gets the value of a varbit in the inventory.
     *
     * @param slot     The inventory slot to check.
     * @param varbitId The varbit id to check.
     * @return The value of the varbit.
     */
    public static int getVarbitValue(int slot, int varbitId) {
        var item = InventoryItemQuery.newQuery(95).slots(slot).results().first();
        return item != null ? item.getVarbitValue(varbitId) : Integer.MIN_VALUE;
    }

    public static boolean setTransferOption(TransferOptionType transferoptionType) {
        var depositOptionState = VarManager.getVarbitValue(WITHDRAW_TYPE_VARBIT_ID);
        return depositOptionState == transferoptionType.getVarbitStateValue() || MiniMenu.interact(ComponentAction.COMPONENT.getType(), 1,-1, 33882215);
    }

    public static int getPreviousLoadedPreset() {
        return previousLoadedPreset;
    }

    /**
     * Interacts with a box in the backpack to empty its contents into the bank.
     *
     * @param boxName The name (or partial name) of the box to interact with.
     * @param option  The interaction option (e.g., "Empty").
     * @return {@code true} if the box was successfully emptied, {@code false} otherwise.
     */
    public static boolean emptyBox(String boxName, String option) {
        // Find the box in the backpack
        var box = Backpack.getItems().stream()
                .filter(item -> item.getName() != null && item.getName().contains(boxName))
                .findFirst()
                .orElse(null);

        if (box == null) {
            // Box not found in the backpack
            return false;
        }

        // Ensure the bank interface is open
        if (!isOpen()) {
            return false;
        }

        // Query the bank interface for the specific option to empty the box
        var component = ComponentQuery.newQuery(517)
                .option(option)
                .results()
                .first();

        if (component == null) {
            // The specified interaction option was not found in the bank interface
            return false;
        }

        // Interact with the option to empty the box
        if (component.interact(option)) {
            Execution.delay(1500); // Allow time for the interaction to complete
            return true;
        }

        // Interaction failed
        return false;
    }


    /**
     * Handles the complete banking process including depositing items, emptying boxes, and closing the bank.
     *
     * @param itemToBank The name of the item to deposit.
     * @param boxName The name of the box to empty.
     * @param option The interaction option for the box.
     * @param maxAttempts The maximum number of retry attempts.
     * @return {@code true} if the banking process was successful, {@code false} otherwise.
     */
    public static boolean handleBanking(String itemToBank, String boxName, String option, int maxAttempts) {
        if (!openWithRetries(maxAttempts)) {
            return false;
        }
        if (!depositAllWithRetries(itemToBank, maxAttempts)) {
            return false;
        }
        if (!emptyBox(boxName, option)) { // This method would need to be added to handle emptying boxes.
            return false;
        }
        return closeWithRetries(maxAttempts);
    }

}

enum TransferOptionType {
    ONE(2),
    FIVE(3),
    TEN(4),
    ALL(7),
    X(5);

    private final int varbitStateValue;

    TransferOptionType(int varbitStateValue) {
        this.varbitStateValue = varbitStateValue;
    }

    public int getVarbitStateValue() {
        return varbitStateValue;
    }
}
