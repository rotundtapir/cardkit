// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.cardkit.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.PolymorphicModuleBuilder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.SerializersModuleBuilder
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlin.reflect.KClass

/**
 * The messages every cardkit game shares, registered against the open [ClientMessage]/[ServerMessage]
 * bases. A game combines this with its own payload registrations — see [gameWireModule].
 */
val commonWireModule: SerializersModule = SerializersModule {
    polymorphic(ClientMessage::class) {
        subclass(Hello::class, Hello.serializer())
        subclass(JoinLobby::class, JoinLobby.serializer())
        subclass(SetName::class, SetName.serializer())
        subclass(PickSeat::class, PickSeat.serializer())
        subclass(SetReady::class, SetReady.serializer())
        subclass(ConfigureLobby::class, ConfigureLobby.serializer())
        subclass(StartGame::class, StartGame.serializer())
        subclass(LeaveLobby::class, LeaveLobby.serializer())
        subclass(DisbandLobby::class, DisbandLobby.serializer())
        subclass(RequestRematch::class, RequestRematch.serializer())
        subclass(SendEmote::class, SendEmote.serializer())
    }
    polymorphic(ServerMessage::class) {
        subclass(Welcome::class, Welcome.serializer())
        subclass(UpdateRequired::class, UpdateRequired.serializer())
        subclass(SeatStatus::class, SeatStatus.serializer())
        subclass(GameOver::class, GameOver.serializer())
        subclass(EmoteReceived::class, EmoteReceived.serializer())
        subclass(LobbyDisbanded::class, LobbyDisbanded.serializer())
        subclass(ErrorMessage::class, ErrorMessage.serializer())
    }
}

/**
 * Registers the game-parameterised messages plus the game's own [CreateLobbyRequest] implementation.
 *
 * Exactly one instantiation of each generic message is registered per module, which is why a server
 * process (and a client) speaks exactly one game — the erased [KClass] of, say, [ViewUpdate] can map
 * to only one concrete view serializer. That is a deliberate constraint, not a limitation to work
 * around: two games are two processes (or at minimum two [Json] instances), never one ambiguous wire.
 */
fun <A : Any, V : Any, C : Any, R : CreateLobbyRequest> gameWireModule(
    actionSerializer: KSerializer<A>,
    viewSerializer: KSerializer<V>,
    configSerializer: KSerializer<C>,
    createLobbyClass: KClass<R>,
    createLobbySerializer: KSerializer<R>,
    extra: SerializersModuleBuilder.() -> Unit = {},
): SerializersModule = SerializersModule {
    polymorphic(ClientMessage::class) {
        subclass(createLobbyClass, createLobbySerializer)
        submitAction(actionSerializer)
    }
    polymorphic(ServerMessage::class) {
        viewUpdate(viewSerializer)
        lobbyState(configSerializer)
    }
    extra()
}

/**
 * The single JSON configuration used on both ends of the wire, over [commonWireModule] plus the
 * game's own registrations. Kept identical client- and server-side so a frame encoded by one decodes
 * on the other.
 *
 *  - [Json.ignoreUnknownKeys]: newer peers may add fields; older peers skip them (forward compat).
 *  - [Json.coerceInputValues]: an unknown enum value coerces to that enum's default member — every
 *    forward-compat enum defaults to its `UNKNOWN` case, so new emotes/error codes never break an
 *    old client (paired with the `= …UNKNOWN` defaults on message fields carrying those enums).
 *  - [Json.encodeDefaults] off: fields equal to their default are omitted, which is exactly what
 *    makes adding an optional field a non-breaking change.
 *  - `classDiscriminator = "type"` is the kotlinx default, pinned here so a library default change
 *    can never silently alter the wire format.
 */
fun wireJson(gameModule: SerializersModule): Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = false
    classDiscriminator = "type"
    serializersModule = commonWireModule + gameModule
}

/**
 * Registers [SubmitAction] for the game's action type.
 *
 * The cast is safe because polymorphic registration keys on the erased class, and a module registers
 * a single instantiation (see [gameWireModule]): at runtime every `SubmitAction` instance flowing
 * through this [Json] carries an `A`.
 */
@Suppress("UNCHECKED_CAST")
fun <A : Any> PolymorphicModuleBuilder<ClientMessage>.submitAction(actionSerializer: KSerializer<A>) {
    subclass(
        SubmitAction::class as KClass<SubmitAction<A>>,
        SubmitAction.serializer(actionSerializer),
    )
}

/** Registers [ViewUpdate] for the game's view type. See [submitAction] on why the cast is safe. */
@Suppress("UNCHECKED_CAST")
fun <V : Any> PolymorphicModuleBuilder<ServerMessage>.viewUpdate(viewSerializer: KSerializer<V>) {
    subclass(
        ViewUpdate::class as KClass<ViewUpdate<V>>,
        ViewUpdate.serializer(viewSerializer),
    )
}

/** Registers [LobbyState] for the game's config type. See [submitAction] on why the cast is safe. */
@Suppress("UNCHECKED_CAST")
fun <C : Any> PolymorphicModuleBuilder<ServerMessage>.lobbyState(configSerializer: KSerializer<C>) {
    subclass(
        LobbyState::class as KClass<LobbyState<C>>,
        LobbyState.serializer(configSerializer),
    )
}
