package com.sablednah.factions;

import com.sablednah.standards.neoforge.Lang;

/**
 * Every string this mod can say, contributed to Standards' catalogue.
 *
 * <p>One {@code messages.yml} for the whole server. A shipping a second string system would mean
 * a second set of colour rules, a second placeholder syntax and a second merge-on-upgrade
 * behaviour for an owner to learn — and they would have to find two files to rename one word.</p>
 *
 * <p>Contributed keys get the {@code {term.*}} substitutions for free, including Standards' own
 * terms. So {@code {term.home}} here follows a rename this mod never knew about, and
 * {@code term.faction} is defined once and used by both.</p>
 */
public final class FactionLang {

    public static void contribute() {
        // Vocabulary. A server calling them clans or houses changes these two.
        Lang.contribute("term.faction", "faction");
        Lang.contribute("term.factions", "factions");

        Lang.contribute("msg.factions.none",
                "{term.prefix} &7You are not in a {term.faction}. {term.dim}(/f create <name>)");
        Lang.contribute("msg.factions.none_yet", "{term.prefix} &7No {term.factions} yet.");
        Lang.contribute("msg.factions.unknown", "&cNo {term.faction} called &f{name}&c.");
        Lang.contribute("msg.factions.created",
                "{term.prefix} &7Founded &f{name}&7. {term.dim}(/f claim to take the land you are stood on)");
        Lang.contribute("msg.factions.name_taken", "&cSomething is already called &f{name}&c.");
        Lang.contribute("msg.factions.name_length",
                "&cA faction name must be between one and &f{max}&c characters.");
        Lang.contribute("msg.factions.name_bad",
                "&cThat name will not do. {term.dim}(no colour codes, no double or trailing spaces, and at least one letter or digit)");
        Lang.contribute("msg.factions.already_in_one",
                "&cYou are already in a {term.faction} — leave it first.");
        Lang.contribute("msg.factions.they_are_in_one",
                "&c{player} is already in a {term.faction}.");
        Lang.contribute("msg.factions.need_rank", "&cYou must be at least &f{rank}&c to do that.");
        Lang.contribute("msg.factions.outranked", "&cThey outrank you.");

        // Raids. Announced to the whole server on purpose — a raid nobody could see would be a
        // war with extra steps, and half the value is that other people can turn up.
        Lang.contribute("msg.factions.raid_declared",
                "{term.prefix} &c&lRAID&r &f{attacker}&7 is attacking &f{defender}&7 — &f{time}&7 to take their standard and plant it at home.");
        Lang.contribute("msg.factions.raid_bar", "&6&lRAID&r &f{time}");
        Lang.contribute("msg.factions.raid_bar_urgent", "&c&lRAID&r &c&l{time}");
        Lang.contribute("msg.factions.raid_bar_over", "&8&lRAID OVER");
        Lang.contribute("msg.factions.raid_top_header",
                "{term.prefix} &6&lRAIDS&r &7— won, of fought. {term.dim}(taken = raids you won attacking; held = raids you won defending)");
        Lang.contribute("msg.factions.raid_top_row",
                "  &7{place}. &f{name}&7 — &f{won}&7 of &f{fought}&7 {term.dim}(taken {taken}, held {held})");
        Lang.contribute("msg.factions.raid_top_none",
                "&7Nobody has finished a raid yet. {term.dim}(the board fills itself the first time somebody tries)");
        Lang.contribute("msg.factions.who_raids",
                "&7Raids: &f{won}&7 won of &f{fought}&7 {term.dim}(taken {taken}, held {held})");
        Lang.contribute("msg.factions.raid_over_planted",
                "&6&l⚔ &r&f{attacker}&6 has planted &f{defender}&6's standard on their own ground. The raid is won.");
        Lang.contribute("msg.factions.raid_over_land",
                "&6&l⚔ &r&f{attacker}&6 has taken ground from &f{defender}&6, who flew no standard to defend. The raid is won.");
        Lang.contribute("msg.factions.raid_over_repelled",
                "{term.prefix} &a&lREPELLED&r &f{defender}&7 drove &f{attacker}&7 off.");
        Lang.contribute("msg.factions.raid_over_held",
                "{term.prefix} &a&lHELD&r &f{defender}&7 held out against &f{attacker}&7.");
        Lang.contribute("msg.factions.raid_self", "&cRaiding yourself would prove very little.");
        Lang.contribute("msg.factions.raid_peaceful",
                "&cPeaceful {term.factions} neither raid nor are raided.");
        Lang.contribute("msg.factions.raid_already", "&cThere is already a raid running.");
        Lang.contribute("msg.factions.raid_cooldown",
                "&cYou raided &f{name}&c too recently — &f{time}&c to wait.");
        // The refusal that replaces declining, so it has to explain itself rather than just say no.
        Lang.contribute("msg.factions.raid_nobody_home",
                "&cNobody is home. &f{name}&c needs &f{needed}&c member(s) online to be raided, and has &f{online}&c.");
        Lang.contribute("msg.factions.raid_no_standard",
                "{term.prefix} &e{name} flies no standard&7 — there is nothing to take yet. The raid still counts if they plant one.");
        Lang.contribute("msg.factions.raid_claim_limit",
                "&cThis raid has taken its &f{count}&c chunk(s). Another raid, another chunk.");
        Lang.contribute("msg.factions.raid_none", "{term.prefix} &7No raids are running.");
        Lang.contribute("msg.factions.raid_row",
                " &f{attacker} {term.dim}-> raiding&r &f{defender} {term.dim}({time} left)");
        Lang.contribute("msg.factions.raid_needed",
                "&cLand only changes hands during a declared raid. {term.dim}(/f raid {name})");

        // The claim override. Worded to keep it feeling temporary, because it is.
        Lang.contribute("msg.factions.bypass_on",
                "{term.prefix} &eClaim override ON&7 — you can edit anybody's land. {term.dim}(/f bypass off, and it drops when you log out)");
        Lang.contribute("msg.factions.bypass_off",
                "{term.prefix} &7Claim override off. {term.faction} land is protected from you again.");
        Lang.contribute("msg.factions.bypass_already",
                "{term.prefix} &7Claim override was already {state}&7.");
        Lang.contribute("msg.factions.bypass_used",
                "{term.dim}(override — this is &f{faction}&r{term.dim} land)");

        Lang.contribute("msg.factions.rank.member", "member");
        Lang.contribute("msg.factions.rank.officer", "officer");
        Lang.contribute("msg.factions.rank.leader", "leader");
        Lang.contribute("msg.factions.rank_set", "{term.prefix} &f{player}&7 is now a &f{rank}&7.");
        Lang.contribute("msg.factions.rank_unchanged", "&cThere is no rank to move them to.");

        Lang.contribute("msg.factions.invited", "{term.prefix} &7Invited &f{player}&7.");
        Lang.contribute("msg.factions.invite_received",
                "{term.prefix} &f{player}&7 invited you to &f{name}&7. {term.dim}(/f join {name})");
        Lang.contribute("msg.factions.not_invited", "&cYou have not been invited to &f{name}&c.");

        // Asking to join — the invitation, the other way round.
        Lang.contribute("msg.factions.requested",
                "{term.prefix} &7Asked to join &f{name}&7. {term.dim}(they will be told)");
        Lang.contribute("msg.factions.already_asked", "&cYou have already asked &f{name}&c.");
        Lang.contribute("msg.factions.request_received",
                "{term.prefix} &f{player}&7 wants to join. {term.dim}(/f accept {player} — or /f decline)");
        Lang.contribute("msg.factions.no_requests", "{term.prefix} &7Nobody is waiting to join.");
        Lang.contribute("msg.factions.requests_header",
                "{term.prefix} &7Waiting to join {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.no_request_from", "&c{player} has not asked to join.");
        Lang.contribute("msg.factions.accepted", "{term.prefix} &7Let &f{player}&7 in.");
        Lang.contribute("msg.factions.declined", "{term.prefix} &7Turned &f{player}&7 down.");
        Lang.contribute("msg.factions.you_were_declined",
                "{term.prefix} &f{name}&7 turned you down.");
        Lang.contribute("msg.factions.joined", "{term.prefix} &7You joined &f{name}&7.");
        Lang.contribute("msg.factions.member_joined", "{term.prefix} &f{player}&7 joined.");
        Lang.contribute("msg.factions.member_left", "{term.prefix} &f{player}&7 left.");
        Lang.contribute("msg.factions.you_left", "{term.prefix} &7You left &f{name}&7.");
        Lang.contribute("msg.factions.leader_must_disband",
                "&cA leader cannot walk away from a {term.faction} that still has people in it. Hand it over, or &f/f disband&c.");
        Lang.contribute("msg.factions.not_a_member", "&c{player} is not in your {term.faction}.");
        Lang.contribute("msg.factions.kick_self", "&cUse &f/f leave&c.");
        Lang.contribute("msg.factions.kicked", "{term.prefix} &7Removed &f{player}&7.");
        Lang.contribute("msg.factions.you_were_kicked",
                "{term.prefix} &7You were removed from &f{name}&7.");
        Lang.contribute("msg.factions.disbanded", "{term.prefix} &c{name} has been disbanded.");

        // Land
        Lang.contribute("msg.factions.claimed",
                "{term.prefix} &aClaimed &f{x}, {z}&a. {term.dim}({held}/{limit} chunks)");
        Lang.contribute("msg.factions.already_yours", "&cYou already hold this chunk.");
        Lang.contribute("msg.factions.claimed_by_other", "&cThis land belongs to &f{name}&c.");
        Lang.contribute("msg.factions.claim_limit",
                "&cYour {term.faction} holds &f{held}&c of &f{limit}&c chunks. More members, more land — you have &f{members}&c.");
        Lang.contribute("msg.factions.must_connect",
                "&cClaims must touch land you already hold.");
        Lang.contribute("msg.factions.not_yours", "&cThis is not your land.");
        Lang.contribute("msg.factions.unclaimed", "{term.prefix} &7Released &f{x}, {z}&7.");
        Lang.contribute("msg.factions.unclaimed_all",
                "{term.prefix} &7Released &f{count}&7 chunks.");
        Lang.contribute("msg.factions.unclaimed_all_others",
                "{term.prefix} &c{player} released all &f{count}&c of your chunks.");
        Lang.contribute("msg.factions.no_limit", "unlimited");

        // Power.
        Lang.contribute("msg.factions.power_off", "&7Power is not in use on this server.");
        Lang.contribute("msg.factions.power_regen",
                " {term.dim}-&r &7Power returns at &f{rate}&7 a minute {term.dim}({standard})");
        Lang.contribute("msg.factions.standard_state_none", "no standard");
        Lang.contribute("msg.factions.standard_state_stolen", "your standard is taken");
        Lang.contribute("msg.factions.standard_state_flying", "standard flying");
        Lang.contribute("msg.factions.standard_state_covered", "standard covered — it earns nothing");
        Lang.contribute("msg.factions.standard_state_trophy", "flying a captured standard");

        // The standard.
        Lang.contribute("msg.factions.standard_set",
                "{term.prefix} &7Your standard is raised. {term.dim}({colour} — it must keep seeing the sky)");
        Lang.contribute("msg.factions.standard_not_banner",
                "&cLook at a banner you have placed, and run this again.");
        Lang.contribute("msg.factions.standard_not_your_land",
                "&cA standard stands on land you hold.");
        Lang.contribute("msg.factions.standard_needs_sky",
                "&cIt must stand under open sky. {term.dim}(a flag nobody can reach is a flag nobody can take — and yours would be the same)");
        Lang.contribute("msg.factions.standard_already", "&cThat banner is already a standard.");
        Lang.contribute("msg.factions.standard_none",
                "&7You fly no standard. {term.dim}(place a banner on your land under open sky, look at it, and /f standard)");
        Lang.contribute("msg.factions.standard_where",
                "{term.prefix} &7Your standard stands at &f{x}, {y}, {z}&7 in &f{world}&7.");
        Lang.contribute("msg.factions.standard_trophies",
                "&7You also fly &f{count}&7 captured standard(s). {term.dim}(the power bonus is the same however many — but they have to take every one)");
        Lang.contribute("msg.factions.standard_trophy_line",
                "  &8• &f{name}&7's, at &f{x}, {y}, {z}&7 in &f{world}&7 — {state}");
        Lang.contribute("msg.factions.standard_theirs",
                "&c&f{name}&c is flying your standard at &f{x}, {y}, {z}&c in &f{world}&c. {term.dim}(you cannot raise another while it flies — go and take it back)");
        Lang.contribute("msg.factions.standard_taken",
                "&c&l⚑ &r&f{taker}&c has taken &f{name}&c's standard.");
        Lang.contribute("msg.factions.standard_recovered",
                "&a&l⚑ &r&f{taker}&a has recovered their standard.");
        Lang.contribute("msg.factions.standard_taken_down",
                "{term.prefix} &7Standard taken down. {term.dim}(plant it again to raise it)");
        Lang.contribute("msg.factions.standard_not_the_real_one",
                "&cThat is not the real one, and it has stopped pretending to be. {term.dim}(their standard is accounted for; what you are holding is now an ordinary banner)");
        Lang.contribute("msg.factions.standard_item", "&f{name}&7's standard");
        Lang.contribute("msg.factions.standard_planted_trophy",
                "{term.prefix} &6You are flying &f{name}&6's standard. {term.dim}(they can see where — and come for it)");
        Lang.contribute("msg.factions.standard_trophy_seen",
                "&c&l⚑ &r&f{name}&c is flying your standard at &f{x}, {y}, {z}&c.");
        Lang.contribute("msg.factions.standard_lost",
                "&c&lYour standard has fallen. &r&cPower comes back slower until you raise another.");
        Lang.contribute("msg.factions.standard_covered",
                "&cYour standard no longer sees the sky. {term.dim}(it earns nothing while it is covered)");
        Lang.contribute("msg.factions.standard_uncovered",
                "{term.prefix} &aYour standard sees the sky again.");
        Lang.contribute("msg.factions.standard_gone",
                "&cYour standard is gone. {term.dim}(raise another when you can)");
        Lang.contribute("msg.factions.standard_still_taken",
                "&cYou cannot raise another while &f{name}&c is flying yours. {term.dim}(go and take it back)");
        Lang.contribute("msg.factions.standard_carried",
                "&c&f{player}&c is carrying your standard — &f{x}, {y}, {z}&c in &f{world}&c. {term.dim}(go and get it)");
        Lang.contribute("msg.factions.standard_carried_ours",
                "{term.prefix} &f{player}&7 is carrying it — &f{x}, {y}, {z}&7. {term.dim}(plant it on your land under sky to raise it)");
        Lang.contribute("msg.factions.standard_no_faction",
                "&cThat is a {term.faction} standard. You need a {term.faction} to fly it.");
        Lang.contribute("msg.factions.standard_plant_own_land",
                "&cPlant it on land you hold, and it flies. {term.dim}(here it is just a banner)");
        Lang.contribute("msg.factions.standard_already_flying",
                "&cYou already fly a standard. {term.dim}(take that one down first)");
        Lang.contribute("msg.factions.standard_broken_by_world",
                "&cYour standard has been destroyed. {term.dim}(nobody took it — raise another when you can)");
        Lang.contribute("msg.factions.power_mine",
                "{term.prefix} &f{player}&7 has &f{power}&7 of &f{max}&7 power.");
        Lang.contribute("msg.factions.power_faction",
                " {term.dim}-&r &f{name}&7 holds &f{held}&7 chunks on an entitlement of &f{entitled}&7.");
        Lang.contribute("msg.factions.power_lost",
                "&cYou lost &f{lost}&c power. {term.dim}({power}/{max})");
        // The line the whole feature earns its keep with. A raid nobody knew was possible is
        // indistinguishable from a bug.
        Lang.contribute("msg.factions.power_exposed",
                "&c&lExposed. &r&cYou hold &f{held}&c on an entitlement of &f{entitled}&c — &f{over}&c chunks can be taken.");
        Lang.contribute("msg.factions.status_power",
                " {term.dim}-&r &7Power &f{power}&7 {term.dim}({held} chunks held, {entitled} entitled)");
        Lang.contribute("msg.factions.claim_held",
                "&f{name}&c owns this land and is strong enough to keep it.");
        Lang.contribute("msg.factions.claim_not_at_war",
                "&cYou are not at war with &f{name}&c. {term.dim}(/f enemy {name})");
        Lang.contribute("msg.factions.claim_not_border",
                "&cStart at the edge of their territory, not the middle of it.");
        Lang.contribute("msg.factions.claim_taken",
                "{term.prefix} &aTaken &f{x}, {z}&a from &f{name}&a.");
        Lang.contribute("msg.factions.claim_lost",
                "&c&lLand lost. &r&c&f{name}&c has taken &f{x}, {z}&c from you.");
        Lang.contribute("msg.factions.unknown_player",
                "&cNo player called &f{player}&c has been seen on this server.");
        Lang.contribute("msg.factions.autoclaim_on",
                "{term.prefix} &7Claiming as you walk. {term.dim}(/f autoclaim again to stop)");
        Lang.contribute("msg.factions.autoclaim_off", "{term.prefix} &7No longer claiming as you walk.");
        Lang.contribute("msg.factions.autoclaim_full",
                "&cThat is all the land you have — &f{held}&c of &f{limit}&c chunks.");
        Lang.contribute("msg.factions.autoclaim_lost_rank",
                "&cYou are no longer an officer of anything.");
        Lang.contribute("msg.factions.cannot_build",
                "&cThis land belongs to &f{name}&c.");
        Lang.contribute("msg.factions.cannot_touch",
                "&cThat belongs to &f{name}&c.");

        // Territory, on the action bar
        Lang.contribute("msg.factions.entered", "{colour}{name}");
        Lang.contribute("msg.factions.entered_wild", "&8Wilderness");

        // Home
        Lang.contribute("msg.factions.home_set",
                "{term.prefix} &7{term.faction} {term.home} set {term.dim}({place})&7.");
        Lang.contribute("msg.factions.home_set_others",
                "{term.prefix} &f{player}&7 moved the {term.faction} {term.home}.");
        Lang.contribute("msg.factions.home_must_be_claimed",
                "&cSet your {term.home} on land you hold — one outside it is one an enemy can camp.");
        Lang.contribute("msg.factions.no_home",
                "&cYour {term.faction} has no {term.home}. {term.dim}(/f sethome)");
        Lang.contribute("msg.factions.home_went", "{term.prefix} &7Back to &f{name}&7.");

        // Identity
        Lang.contribute("msg.factions.tag_set", "{term.prefix} &7Tag set to &f[{tag}]&7.");
        Lang.contribute("msg.factions.tag_cleared", "{term.prefix} &7Tag removed.");
        Lang.contribute("msg.factions.tag_taken", "&cAnother {term.faction} already uses &f[{tag}]&c.");
        Lang.contribute("msg.factions.tag_too_long",
                "&cA tag is at most &f{max}&c characters — it goes on every line of chat.");
        Lang.contribute("msg.factions.no_tag", "none");
        Lang.contribute("msg.factions.renamed", "{term.prefix} &f{old}&7 is now &f{name}&7.");

        // Relations
        Lang.contribute("msg.factions.relation.ally", "&ballied");
        Lang.contribute("msg.factions.relation.enemy", "&chostile");
        Lang.contribute("msg.factions.relation.neutral", "&fneutral");
        Lang.contribute("msg.factions.declared",
                "{term.prefix} &7You are now &f{relation}&7 towards &f{name}&7.");
        Lang.contribute("msg.factions.relation_self", "&cYou are already yourselves.");
        Lang.contribute("msg.factions.alliance_pending",
                "{term.prefix} &7Offered. An alliance holds only when &f{name}&7 offers back.");
        Lang.contribute("msg.factions.alliance_offered",
                "{term.prefix} &b{name}&7 has offered you an alliance. {term.dim}(/f ally {name})");
        Lang.contribute("msg.factions.now_peaceful",
                "{term.prefix} &aYour {term.faction} is now peaceful — it cannot fight, and cannot be fought.");
        Lang.contribute("msg.factions.no_longer_peaceful",
                "{term.prefix} &cYour {term.faction} is no longer peaceful.");
        Lang.contribute("msg.factions.peaceful_no_enemies",
                "&cA peaceful {term.faction} has no enemies, in either direction.");
        Lang.contribute("msg.factions.is_peaceful", "&a(peaceful)");

        // Where you stand with everybody, in one place.
        Lang.contribute("msg.factions.status_header",
                "{term.prefix} &f{name}&7 {peaceful}&7— &f{land}&7 chunks, &f{count}&7 members");
        Lang.contribute("msg.factions.status_offered_to_us",
                " {term.dim}-&r &bOffered you an alliance {term.dim}({count})&7: &f{list} {term.dim}(/f ally <name> to agree)");
        Lang.contribute("msg.factions.status_offered_by_us",
                " {term.dim}-&r &7Waiting on {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.status_allies",
                " {term.dim}-&r &bAllied {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.status_we_declared",
                " {term.dim}-&r &cYou declared on {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.status_they_declared",
                " {term.dim}-&r &cDeclared on you {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.status_none_header",
                "{term.prefix} &7You are in no {term.faction}. {term.dim}(/f list — or /f create <name>)");
        Lang.contribute("msg.factions.status_invited",
                " {term.dim}-&r &bInvited by {term.dim}({count})&7: &f{list} {term.dim}(/f join <name>)");
        Lang.contribute("msg.factions.status_asked",
                " {term.dim}-&r &7You have asked {term.dim}({count})&7: &f{list} {term.dim}(waiting on them)");
        Lang.contribute("msg.factions.status_none_pending",
                " {term.dim}-&r &7Nobody has invited you, and you have asked nobody.");
        Lang.contribute("msg.factions.status_bank",
                " {term.dim}-&r &7Bank &a{amount}&7 {term.dim}(next chunk {next})");
        Lang.contribute("msg.factions.status_requests",
                " {term.dim}-&r &7Waiting to join {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.status_nothing",
                " {term.dim}-&r &7At peace with everyone, and nobody is waiting on you.");
        Lang.contribute("msg.factions.pvp_peaceful", "&7They are peaceful.");
        Lang.contribute("msg.factions.pvp_same_faction", "&7They are in your {term.faction}.");
        Lang.contribute("msg.factions.pvp_ally", "&7They are your ally.");
        Lang.contribute("msg.factions.pvp_disabled", "&7Fighting is off on this server.");

        // Looking
        Lang.contribute("msg.factions.who",
                "{term.prefix} &f{name}&7 {term.dim}[{tag}]&7 — {relation}&7 {peaceful}&7, &f{land}&7 chunks, {term.dim}({count})&7: &f{members}");
        Lang.contribute("msg.factions.list_header", "{term.prefix} &7{term.factions} {term.dim}({count})&7:");
        Lang.contribute("msg.factions.list_row",
                " {term.dim}-&r &f{name} {term.dim}{tag} ({count} members, {land} chunks)");

        // Map
        Lang.contribute("msg.factions.map_legend",
                "{term.dim}  &a# yours  &b# allied  &c# hostile  &f# other  &8- wilderness  &e+ you");
        Lang.contribute("msg.factions.map_title", "&bClaims Atlas");
        Lang.contribute("msg.factions.map_given",
                "{term.prefix} &7An atlas of the land around you. {term.dim}({chunks} chunks across)");
        Lang.contribute("msg.factions.map_failed", "&cCould not draw the atlas.");
        Lang.contribute("msg.factions.borders_on",
                "{term.prefix} &7Borders shown. {term.dim}(or just hold the tool)");
        Lang.contribute("msg.factions.borders_off", "{term.prefix} &7Borders hidden.");

        // Test fixtures.
        Lang.contribute("msg.factions.fixtures_seeded",
                "{term.prefix} &7Invented &f{count}&7 neighbours. {term.dim}(/f fixture clear to remove them)");
        Lang.contribute("msg.factions.fixtures_standards",
                "{term.prefix} &7Planted a flag for &f{count}&7 neighbour(s). {term.dim}(go and take one — they are real standards on real land)");
        Lang.contribute("msg.factions.fixtures_row", " {term.dim}-&r &7{row}");
        Lang.contribute("msg.factions.fixtures_cleared",
                "{term.prefix} &7Removed &f{count}&7 invented {term.factions}.");

        // Talking. Green for your own and blue for allies, matching the border and map colours
        // so one palette means the same thing everywhere in the mod.
        Lang.contribute("msg.factions.chat_faction", "&2[&a{tag}&2] &a{player}&2: &a{message}");
        Lang.contribute("msg.factions.chat_ally", "&3[&b{tag}&3] &b{player}&3: &b{message}");
        Lang.contribute("msg.factions.chat_spy",
                "{term.dim}[spy {channel} {faction}] {player}: {message}");
        Lang.contribute("msg.factions.chat_channel.public", "everyone");
        Lang.contribute("msg.factions.chat_channel.faction", "your {term.faction}");
        Lang.contribute("msg.factions.chat_channel.ally", "your {term.faction} and its allies");
        Lang.contribute("msg.factions.chat_now", "{term.prefix} &7Now talking to &f{channel}&7.");
        Lang.contribute("msg.factions.chat_no_faction",
                "&cYou have no {term.faction} to talk to. {term.dim}(back to public chat)");
        Lang.contribute("msg.factions.chat_alone", "{term.dim}  Nobody else is listening.");
        Lang.contribute("msg.factions.chatspy_on",
                "{term.prefix} &7Watching every {term.faction} channel.");
        Lang.contribute("msg.factions.chatspy_off", "{term.prefix} &7No longer watching.");

        // The bank.
        Lang.contribute("msg.factions.bank_balance",
                "{term.prefix} &f{name}&7 holds &a{amount}&7.");
        Lang.contribute("msg.factions.bank_next_claim",
                " {term.dim}-&r &7The next chunk costs &f{amount}&7.");
        Lang.contribute("msg.factions.bank_deposited",
                "{term.prefix} &7Paid &a{amount}&7 into &f{name}&7.");
        Lang.contribute("msg.factions.bank_withdrew",
                "{term.prefix} &7Took &a{amount}&7 out of &f{name}&7.");
        Lang.contribute("msg.factions.bank_paid",
                "{term.prefix} &7Sent &a{amount}&7 to &f{name}&7.");
        Lang.contribute("msg.factions.bank_received",
                "{term.prefix} &f{name}&7 sent you &a{amount}&7{note}&7.");
        Lang.contribute("msg.factions.bank_short", "&cThere is not &f{amount}&c to move.");
        Lang.contribute("msg.factions.bank_pay_self",
                "&cYou already have it.");
        Lang.contribute("msg.factions.bank_no_economy",
                "&cThere is no economy on this server.");
        Lang.contribute("msg.factions.bank_failed",
                "&cThat did not go through. Nothing has moved — tell an admin.");
        Lang.contribute("msg.factions.claim_too_dear",
                "&cThat chunk costs &f{amount}&c and the bank holds &f{balance}&c. {term.dim}(/f money deposit)");
        Lang.contribute("msg.factions.unclaim_refund",
                " {term.dim}-&r &7&a{amount}&7 back into the bank.");
    }

    private FactionLang() {}
}
