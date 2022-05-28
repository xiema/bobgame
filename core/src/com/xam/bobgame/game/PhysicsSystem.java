package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.GravitationalFieldComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.definitions.MapDefinition;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityType;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.ButtonReleaseEvent;
import com.xam.bobgame.events.classes.HazardContactEvent;
import com.xam.bobgame.events.classes.PickupContactEvent;
import com.xam.bobgame.events.classes.PlayerBallSpawnedEvent;
import com.xam.bobgame.graphics.TextureDef;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.ExpoMovingAverage;
import com.xam.bobgame.utils.MathUtils2;

public class PhysicsSystem extends EntitySystem {
    private ImmutableArray<Entity> entities;
    private ImmutableArray<Entity> gravFieldEntities;

    private World world;
    private Body[] wallBodies;
    private Sprite[] wallSprites;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();
    private float forceFactor = 1;

    private int velIterations = 6, posIterations = 2;
    private float simUpdateStep = GameProperties.SIMULATION_UPDATE_INTERVAL;

    private Filter nullFilter = new Filter();

    public PhysicsSystem(int priority) {
        super(priority);

        nullFilter.maskBits = 0;

        listeners.put(ButtonReleaseEvent.class, new EventListenerAdapter<ButtonReleaseEvent>() {
            @Override
            public void handleEvent(ButtonReleaseEvent event) {
                playerMovement(event.playerId, event.x, event.y, event.holdDuration);
            }
        });
        listeners.put(PlayerBallSpawnedEvent.class, new EventListenerAdapter<PlayerBallSpawnedEvent>() {
            @Override
            public void handleEvent(PlayerBallSpawnedEvent event) {
                Entity entity = ((GameEngine) getEngine()).getEntityById(event.entityId);
                if (entity == null) return;
                PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                pb.body.applyForceToCenter(MathUtils.random() * 500f, MathUtils.random() * 500f, true);
            }
        });

        entityListeners.put(Family.all(PhysicsBodyComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                physicsBody.body = world.createBody(physicsBody.bodyDef);
                PhysicsHistory physicsHistory = new PhysicsHistory(entity);
                physicsBody.body.setUserData(physicsHistory);
                Shape shape = physicsBody.shapeDef.createShape();
                physicsBody.fixtureDef.shape = shape;
                physicsBody.fixture = physicsBody.body.createFixture(physicsBody.fixtureDef);
                physicsBody.fixture.setUserData(new FixtureData(entity, false));
                physicsBody.body.setFixedRotation(true);
                physicsBody.body.setAngularVelocity(physicsBody.bodyDef.angularVelocity);
//                if (!enabled) physicsBody.fixture.setSensor(true);
                shape.dispose();

                GravitationalFieldComponent gravField = ComponentMappers.gravFields.get(entity);
                if (gravField == null) return;
                FixtureDef fd = new FixtureDef();
                fd.isSensor = true;
                fd.shape = new CircleShape();
                fd.shape.setRadius(gravField.radius);
                fd.filter.categoryBits = 8;
                gravField.fixture = physicsBody.body.createFixture(fd);
                gravField.fixture.setUserData(new FixtureData(entity, true));
                fd.shape.dispose();
            }

            @Override
            public void entityRemoved(Entity entity) {
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                GravitationalFieldComponent gravField = ComponentMappers.gravFields.get(entity);
                if (physicsBody.body == null) return;
                if (physicsBody.fixture != null) physicsBody.body.destroyFixture(physicsBody.fixture);
                if (gravField != null && gravField.fixture != null) physicsBody.body.destroyFixture(gravField.fixture);
                world.destroyBody(physicsBody.body);
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        world = new World(new Vector2(0, 0), true);
        world.setContactListener(contactListener);

        MapDefinition mapDefinition = ((GameEngine) engine).getGameDefinitions().getDefinition("0", MapDefinition.class);
        createWalls(mapDefinition);

        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class).get());
        gravFieldEntities = engine.getEntitiesFor(Family.all(GravitationalFieldComponent.class).get());
        EntityUtils.addEntityListeners(engine, entityListeners);

        engine.getSystem(EventsSystem.class).addListeners(listeners);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        world.dispose();
        world = null;
        wallBodies = null;
        wallSprites = null;
        entities = null;
        gravFieldEntities = null;

        EntityUtils.removeEntityListeners(engine, entityListeners);
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
    }

    @Override
    public void update(float deltaTime) {
        for (Entity entity : gravFieldEntities) {
            GravitationalFieldComponent gravField = ComponentMappers.gravFields.get(entity);
            for (Entity entity2 : gravField.affectedEntities) {
                applyGravity(entity, entity2);
            }
        }

        world.step(simUpdateStep, velIterations, posIterations);
        quantizePhysics();
        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) updateSmoothing(deltaTime);
    }

    private void createWalls(MapDefinition mapDefinition) {
        BodyDef bodyDef = new BodyDef();
        PolygonShape shape = new PolygonShape();

        wallBodies = new Body[mapDefinition.walls.size()];
        wallSprites = new Sprite[mapDefinition.walls.size()];

        for (int i = 0; i < mapDefinition.walls.size(); ++i) {
            MapDefinition.Wall wall = mapDefinition.walls.get(i);
            bodyDef.position.set(wall.x, wall.y);
            shape.setAsBox(wall.w, wall.h);

            wallBodies[i] = world.createBody(bodyDef);
            FixtureDef fixtureDef = new FixtureDef();
            fixtureDef.shape = shape;
            fixtureDef.density = 0;
            fixtureDef.filter.categoryBits = 4;
            wallBodies[i].createFixture(fixtureDef);

            if (!BoBGame.isHeadless()) {
                TextureDef textureDef = new TextureDef();
                textureDef.wh = 32;
                textureDef.type = TextureDef.TextureType.Wall;
                textureDef.color.set(Color.LIGHT_GRAY);
                textureDef.textureVal1 = 32;
                textureDef.textureVal2 = 32;
                Texture texture = textureDef.createTexture();
                Sprite sprite = new Sprite(new TextureRegion(texture));
                sprite.setSize(wall.w, wall.h);
                sprite.setOriginCenter();
                sprite.setOriginBasedPosition(wall.x, wall.y);
    //            sprite.setPosition(wall.x, wall.y);
                wallSprites[i] = sprite;
            }
        }

        shape.dispose();
    }

    public Sprite[] getWallSprites() {
        return wallSprites;
    }

    public void clearForces() {
        world.clearForces();
    }

    private final Vector2 tempVec = new Vector2();
    private final Vector2 tempVec2 = new Vector2();
    private final Vector2 tempVec3 = new Vector2();

    private void quantizePhysics() {
        for (Entity entity : entities) {
            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
            Body body = physicsBody.body;
            Transform tfm = body.getTransform();
            Vector2 linearVel = body.getLinearVelocity();

            float x = MathUtils2.quantize(tfm.vals[0], NetDriver.RES_POSITION);
            float y = MathUtils2.quantize(tfm.vals[1], NetDriver.RES_POSITION);
            body.setTransform(x, y, MathUtils2.quantize(tfm.getRotation(), NetDriver.RES_ORIENTATION));
            body.setLinearVelocity(MathUtils2.quantize(linearVel.x, NetDriver.RES_VELOCITY), MathUtils2.quantize(linearVel.y, NetDriver.RES_VELOCITY));
            body.setAngularVelocity(MathUtils2.quantize(body.getAngularVelocity(), NetDriver.RES_ORIENTATION));

            MassData md = body.getMassData();
            md.mass = MathUtils2.quantize(md.mass, NetDriver.RES_MASS);
            md.I = MathUtils2.quantize(md.I, NetDriver.RES_MASS);
            body.setMassData(md);

            tempVec.set(x, y).sub(physicsBody.prevPos);
            physicsBody.xJitterCount = tempVec.x * physicsBody.displacement.x < 0 ? Math.min(3, physicsBody.xJitterCount + 1) : Math.max(0, physicsBody.xJitterCount - 1);
            physicsBody.yJitterCount = tempVec.y * physicsBody.displacement.y < 0 ? Math.min(3, physicsBody.yJitterCount + 1) : Math.max(0, physicsBody.yJitterCount - 1);
            physicsBody.prevPos.set(x, y);
            physicsBody.displacement.set(tempVec);
        }
    }

    private void updateSmoothing(float deltaTime) {
        for (Entity entity : entities) {
            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
            Body body = physicsBody.body;
            PhysicsHistory physicsHistory = (PhysicsHistory) body.getUserData();
            if (physicsHistory == null || physicsHistory.velError <= 0) continue;

            physicsHistory.posXError.update(physicsHistory.velXError.getAverage() * deltaTime * NetDriver.VEL_SMOOTHING_FACTOR);
            physicsHistory.posYError.update(physicsHistory.velYError.getAverage() * deltaTime * NetDriver.VEL_SMOOTHING_FACTOR);
            physicsHistory.velXError.update(0);
            physicsHistory.velYError.update(0);
        }
    }

    private void playerMovement(int playerId, float x, float y, float holdDuration) {
        Log.debug("Player " + playerId + " ButtonRelease " + holdDuration);

        RefereeSystem refereeSystem = getEngine().getSystem(RefereeSystem.class);
        Entity entity = refereeSystem.getPlayerEntity(playerId);
        if (entity == null) return;
        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        PlayerInfo playerInfo = refereeSystem.getPlayerInfo(playerId);

        float chargeAmount = (holdDuration * GameProperties.CHARGE_RATE) % (2 * playerInfo.stamina);
        chargeAmount = (chargeAmount <= playerInfo.stamina ? chargeAmount : (playerInfo.stamina * 2 - chargeAmount)) / GameProperties.PLAYER_STAMINA_MAX;
        float strength = GameProperties.PLAYER_FORCE_STRENGTH * chargeAmount;
//                    Log.info("strength=" + strength);

        // movement direction
        tempVec.set(x, y).sub(pb.body.getPosition()).nor();
        // projection of current vel to movement direction
        tempVec2.set(pb.body.getLinearVelocity()).scl(pb.body.getMass() / simUpdateStep);
        float scalarProj = tempVec2.dot(tempVec);
        // negative rejection of current vel to movement direction
        tempVec3.set(tempVec).scl(scalarProj).sub(tempVec2);
        float strengthProj = Math.max(0, strength - tempVec3.len());
        // target accel along movement direction
        tempVec2.set(tempVec).scl(strengthProj);
        // target accel perpendicular to movement direction
        tempVec3.add(tempVec2);

        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) {
            PhysicsHistory physicsHistory = (PhysicsHistory) pb.body.getUserData();
//            physicsHistory.posXError.reset();
//            physicsHistory.posYError.reset();
            physicsHistory.velXError.reset();
            physicsHistory.velYError.reset();
            physicsHistory.velXError.update(tempVec2.x - tempVec3.x);
            physicsHistory.velYError.update(tempVec2.y - tempVec3.y);
            physicsHistory.velError = 4;
            pb.body.applyForceToCenter(tempVec3.scl(NetDriver.FORCE_FACTOR), true);
        }
        else {
            pb.body.applyForceToCenter(tempVec3, true);
        }

        playerInfo.stamina = Math.max(GameProperties.PLAYER_STAMINA_MIN, playerInfo.stamina - chargeAmount * GameProperties.PLAYER_STAMINA_LOSS);
    }

    public void setForceFactor(float forceFactor) {
        this.forceFactor = forceFactor;
    }

    public float getForceFactor() {
        return forceFactor;
    }

    public void setVelIterations(int velIterations) {
        this.velIterations = velIterations;
    }

    public void setPosIterations(int posIterations) {
        this.posIterations = posIterations;
    }

    public int getVelIterations() {
        return velIterations;
    }

    public int getPosIterations() {
        return posIterations;
    }

    public void setSimUpdateStep(float simUpdateStep) {
        this.simUpdateStep = simUpdateStep;
    }

    public float getSimUpdateStep() {
        return simUpdateStep;
    }

    private Entity queryEntity = null;

    public Entity queryAABB(float x1, float y1, float x2, float y2) {
        queryEntity = null;
        world.QueryAABB(entityQueryCallback, x1, y1, x2, y2);
        return queryEntity;
    }

    private QueryCallback entityQueryCallback = new QueryCallback() {
        @Override
        public boolean reportFixture(Fixture fixture) {
            queryEntity = ((PhysicsHistory) fixture.getBody().getUserData()).entity;
            return false;
        }
    };

    private ContactListener contactListener = new ContactListener() {

        @Override
        public void beginContact(Contact contact) {
            Object ud1 = contact.getFixtureA().getUserData();
            Object ud2 = contact.getFixtureB().getUserData();
            FixtureData fd1 = ud1 == null ? null : (FixtureData) ud1;
            FixtureData fd2 = ud2 == null ? null : (FixtureData) ud2;
            if (fd1 == null || fd2 == null) return;
            if (fd1.isGravField) {
                ComponentMappers.gravFields.get(fd1.entity).affectedEntities.add(fd2.entity);
            }
            else if (fd2.isGravField) {
                ComponentMappers.gravFields.get(fd2.entity).affectedEntities.add(fd1.entity);
            }
            else {
                EntityType type1 = ComponentMappers.identity.get(fd1.entity).type;
                EntityType type2 = ComponentMappers.identity.get(fd2.entity).type;
                GameEvent event = null;
                if (type1 == EntityType.Hazard && type2 == EntityType.Player) {
                    HazardContactEvent hazardEvent = Pools.obtain(HazardContactEvent.class);
                    hazardEvent.entity = fd2.entity;
                    hazardEvent.hazard = fd1.entity;
                    event = hazardEvent;
                }
                else if (type2 == EntityType.Hazard && type1 == EntityType.Player) {
                    HazardContactEvent hazardEvent = Pools.obtain(HazardContactEvent.class);
                    hazardEvent.entity = fd1.entity;
                    hazardEvent.hazard = fd2.entity;
                    event = hazardEvent;
                }
                if (event != null) {
                    getEngine().getSystem(EventsSystem.class).queueEvent(event);
                }
            }
        }

        @Override
        public void endContact(Contact contact) {
            Object ud1 = contact.getFixtureA().getUserData();
            Object ud2 = contact.getFixtureB().getUserData();
            FixtureData fd1 = ud1 == null ? null : (FixtureData) ud1;
            FixtureData fd2 = ud2 == null ? null : (FixtureData) ud2;
            if (fd1 == null || fd2 == null) return;
            if (fd1.isGravField) {
                ComponentMappers.gravFields.get(fd1.entity).affectedEntities.removeValue(fd2.entity, true);
            }
            else if (fd2.isGravField) {
                ComponentMappers.gravFields.get(fd2.entity).affectedEntities.removeValue(fd1.entity, true);
            }
        }

        @Override
        public void preSolve(Contact contact, Manifold oldManifold) {
            Fixture f1 = contact.getFixtureA();
            Fixture f2 = contact.getFixtureB();
            Object ud1 = f1.getUserData();
            Object ud2 = f2.getUserData();
            FixtureData fd1 = ud1 == null ? null : (FixtureData) ud1;
            FixtureData fd2 = ud2 == null ? null : (FixtureData) ud2;
            if (fd1 == null || fd2 == null) return;
            EntityType type1 = ComponentMappers.identity.get(fd1.entity).type;
            EntityType type2 = ComponentMappers.identity.get(fd2.entity).type;
            GameEvent event = null;
            if (type2 == EntityType.Pickup && type1 != EntityType.Pickup) {
                PickupContactEvent pickupEvent = Pools.obtain(PickupContactEvent.class);
                pickupEvent.entity = fd1.entity;
                pickupEvent.pickup = fd2.entity;
                event = pickupEvent;
                f2.setFilterData(nullFilter);
                contact.setEnabled(false);
            }
            else if (type1 == EntityType.Pickup && type2 != EntityType.Pickup) {
                PickupContactEvent pickupEvent = Pools.obtain(PickupContactEvent.class);
                pickupEvent.entity = fd2.entity;
                pickupEvent.pickup = fd1.entity;
                event = pickupEvent;
                f1.setFilterData(nullFilter);
                contact.setEnabled(false);
            }
            if (event != null) {
                getEngine().getSystem(EventsSystem.class).queueEvent(event);
            }
        }

        @Override
        public void postSolve(Contact contact, ContactImpulse impulse) {

        }
    };

    private Vector2 gravVec = new Vector2();

    private void applyGravity(Entity entity1, Entity entity2) {
        GravitationalFieldComponent gravField = ComponentMappers.gravFields.get(entity1);
        PhysicsBodyComponent pb1 = ComponentMappers.physicsBody.get(entity1);
        PhysicsBodyComponent pb2 = ComponentMappers.physicsBody.get(entity2);

        Vector2 pos1 = pb1.body.getPosition();
        Vector2 pos2 = pb2.body.getPosition();
        float dx = pos1.x - pos2.x, dy = pos1.y - pos2.y;
        float d2 = dx * dx + dy * dy;
        float d = (float) Math.sqrt(d2);
        gravVec.set(pb1.body.getPosition()).sub(pb2.body.getPosition()).scl(1f / d).scl(gravField.strength / d2);

        pb2.body.applyForceToCenter(gravVec, true);
    }

    private static Entity getEntity(Fixture fixture) {
        Object userData = fixture.getUserData();
        return userData != null ? ((FixtureData) userData).entity : getEntity(fixture.getBody());
    }

    private static Entity getEntity(Body body) {
        Object userData = body.getUserData();
        return userData == null ? null : ((PhysicsHistory) userData).entity;
    }

    public static class PhysicsHistory {
        public final ExpoMovingAverage posXError = new ExpoMovingAverage(0.1f);
        public final ExpoMovingAverage posYError = new ExpoMovingAverage(0.1f);

        public int velError = 0;
        public final ExpoMovingAverage velXError = new ExpoMovingAverage(0.1f);
        public final ExpoMovingAverage velYError = new ExpoMovingAverage(0.1f);

        public final Entity entity;

        public PhysicsHistory(Entity entity) {
            this.entity = entity;
        }

        public void updatePosition(float newX, float newY, float oldX, float oldY) {
            if (posXError.isInit()) {
                posXError.update(newX - (oldX + posXError.getAverage()));
                posYError.update(newY - (oldY + posYError.getAverage()));
            }
            else {
                posXError.update(0);
                posYError.update(0);
            }
        }

        public void updateVel(float newX, float newY, float oldX, float oldY) {
            if (velError == 0) return;
            velError--;
//            velXError.update(newX - (oldX + velXError.getAverage()));
//            velYError.update(newY - (oldY + velYError.getAverage()));
            velXError.update(newX);
            velYError.update(newY);
        }
    }

    public static class FixtureData {
        public final Entity entity;
        public final boolean isGravField;

        public FixtureData(Entity entity, boolean isGravField) {
            this.entity = entity;
            this.isGravField = isGravField;
        }
    }
}
