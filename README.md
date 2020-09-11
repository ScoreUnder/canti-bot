# Canti bot

A small Discord bot intended for a certain Japanese-English language exchange server. Its name comes from Canti, a (robot) character from FLCL.

The bot is running as Canti#0181 (309878089746219008). You can invite it to your server [here][bot-invite].

## Goals

- Robustness
- Ease-of-use for people who are not technically inclined (where possible)
- Lack of feature overlap with other major bots (e.g. if the feature exists in Mee6, Tatsumaki, Nadeko, TypicalBot, etc. then it probably won't find its way here)

## Features

- Voice roles. Non-deafened non-AFK voice chat users get a role, which you may or may not want to colour or hoist. Intended to liven up voice chats on our server and it worked!
- User-owned voice chats. Users can create their own voice channels (which disappear when empty). Useful for breaking up larger rooms into more comfortable small rooms, or for groups to split based on which game they're in.
- Voice vote-kick. Lets voice chat users take matters into their own hands without waiting for a moderator if someone is disrupting them.
- Message quoting. Using a message ID or message link, the bot can retrieve and embed another message. This can let you recall a question to answer it in context, for example.
- Furigana rendering. Using the `&furigana` or `&reading` command, the bot can draw furigana above kanji.
- ID resolving by name. Roles, users and emoji can be searched for with the `&find` command.
- Spoilers. This was from before Discord offered that functionality, so not very useful right now.

## Setup

To host this bot yourself:

1. Create your bot on the [Discord developer portal][discord-developer]
2. Copy `application.conf.example` and name it `application.conf`
3. Edit `application.conf`, filling in `token` with the bot's token from the developer portal. Quotes are required.
4. Again edit `application.conf`, changing `owner` to your own Discord user ID. No quotes here.
5. Run the bot with `sbt run`. If you don't have scala and sbt installed, you can instead download the jar from the [releases][releases] page and run it with `java -jar bot-prod*.jar`
6. Done!

## Can I report a bug?

Yes, use the [issues][issues] page.

[discord-developer]: https://discord.com/developers/
[releases]: https://github.com/ScoreUnder/canti-bot/releases
[issues]: https://github.com/ScoreUnder/canti-bot/issues
[bot-invite]: https://discord.com/oauth2/authorize?scope=bot&client_id=309878089746219008&permissions=285220880
