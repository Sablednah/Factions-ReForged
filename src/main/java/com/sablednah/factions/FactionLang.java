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
    }

    private FactionLang() {}
}
