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
        Lang.contribute("msg.factions.already_in_one",
                "&cYou are already in a {term.faction} — leave it first.");
        Lang.contribute("msg.factions.they_are_in_one",
                "&c{player} is already in a {term.faction}.");
        Lang.contribute("msg.factions.need_rank", "&cYou must be at least &f{rank}&c to do that.");
        Lang.contribute("msg.factions.outranked", "&cThey outrank you.");

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
        Lang.contribute("msg.factions.autoclaim_on",
                "{term.prefix} &7Claiming as you walk. {term.dim}(/f autoclaim again to stop)");
        Lang.contribute("msg.factions.autoclaim_off", "{term.prefix} &7No longer claiming as you walk.");
        Lang.contribute("msg.factions.autoclaim_full",
                "&cThat is all the land you have — &f{held}&c of &f{limit}&c chunks.");
        Lang.contribute("msg.factions.autoclaim_lost_rank",
                "&cYou are no longer an officer of anything.");
        Lang.contribute("msg.factions.cannot_build",
                "&cThis land belongs to &f{name}&c.");

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
        Lang.contribute("msg.factions.status_bank",
                " {term.dim}-&r &7Bank &a{amount}&7 {term.dim}(next chunk {next})");
        Lang.contribute("msg.factions.status_requests",
                " {term.dim}-&r &7Waiting to join {term.dim}({count})&7: &f{list}");
        Lang.contribute("msg.factions.status_nothing",
                " {term.dim}-&r &7At peace with everyone, and nobody is waiting on you.");
        Lang.contribute("msg.factions.pvp_peaceful", "&7They are peaceful.");
        Lang.contribute("msg.factions.pvp_same_faction", "&7They are in your {term.faction}.");
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
                "{term.prefix} &f{name}&7 sent you &a{amount}&7.");
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
