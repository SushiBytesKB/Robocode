package student;

import robocode.*;
import robocode.util.*;

import java.awt.geom.Point2D;
import java.awt.Color;

public class Beast extends TeamRobot 
{
    // FSM
    private enum State 
    {
        SEARCHING, 
        RAMMING
    }

    private State currentState = State.SEARCHING;

    private long lastScanTime = 0;
    private double lastEnemyHeading = 0;

    public void run() 
    {
        // Detach stuff
        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);
        setAdjustRadarForRobotTurn(true);
        setColors(Color.BLACK, Color.BLACK, Color.ORANGE); // Ichigo Kurosaki colors lmao

        while (true) 
        {
            if (getTime() - lastScanTime > 2) 
            {
                currentState = State.SEARCHING;
            }

            if (currentState == State.SEARCHING)
            {
                double centerX = getBattleFieldWidth() / 2;
                double centerY = getBattleFieldHeight() / 2;
                double angleToCenter = Math.atan2(centerX - getX(), centerY - getY());
                
                // getHeadingRadians()
                //"Returns the direction that the robot's body is facing, in radians." - https://robocode.sourceforge.io/docs/robocode/
                setTurnRightRadians(Utils.normalRelativeAngle(angleToCenter - getHeadingRadians()));
                setAhead(100); // We use set for queing actions

                // 360 radar spam lmao
                setTurnRadarRightRadians(Double.POSITIVE_INFINITY);
            }

            execute();
        }
    }

    public void onScannedRobot(ScannedRobotEvent event) 
    {
        // Lock on enemy, adds extra turn to keep the lock on enemy in close quarters
        double absBearing = getHeadingRadians() + event.getBearingRadians();
        double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
        double extraTurn = Math.min(Math.atan(36.0 / event.getDistance()), Rules.RADAR_TURN_RATE_RADIANS); // Rules.RADAR_TURN_RATE_RADIANS (10 degress/turn)
        radarTurn += (radarTurn < 0 ? -extraTurn : extraTurn);
        setTurnRadarRightRadians(radarTurn);

        currentState = State.RAMMING;

        // Movement
        setTurnRightRadians(Utils.normalRelativeAngle(absBearing - getHeadingRadians()));
        setAhead(event.getDistance() + 10); // Don't overcommit

        // Attack
        // Power scale: 1.0 (4 * power) - 3.0 ((4 * power) + 2 * (power - 1))
        double bulletPower = Math.min(3.0, Math.max(1.0, 1200.0 / event.getDistance()));
        double bulletVelocity = 20.0 - 3.0 * bulletPower;

        // Circular prediction - the benchmark bots are always moving circular (lol hard coded? I think not!)
        double enemyX = getX() + event.getDistance() * Math.sin(absBearing);
        double enemyY = getY() + event.getDistance() * Math.cos(absBearing);
        double eHeading = event.getHeadingRadians();
        double eVelocity = event.getVelocity();
        double headingChange = ((lastScanTime > 0) && ((getTime() - lastScanTime) <= 3)) ? headingChange = Utils.normalRelativeAngle(eHeading - lastEnemyHeading) / (getTime() - lastScanTime) : 0;
        double predictedX = enemyX;
        double predictedY = enemyY;
        double timeToHit = 0;
        
        // Predict XY of enemy - TODO: Tweak if the guy is missing too much ?
        while (++timeToHit * bulletVelocity < Point2D.distance(getX(), getY(), predictedX, predictedY))
        {
            predictedX += Math.sin(eHeading) * eVelocity;
            predictedY += Math.cos(eHeading) * eVelocity;
            eHeading += headingChange;

            // boundary check
            predictedX = Math.max(18, Math.min(getBattleFieldWidth() - 18, predictedX));
            predictedY = Math.max(18, Math.min(getBattleFieldHeight() - 18, predictedY));
        }

        // DONE: looked up math tutorials to fix all the math
        setTurnGunRightRadians(Utils.normalRelativeAngle(Math.atan2(predictedX - getX(), predictedY - getY()) - getGunHeadingRadians()));
        setFire(bulletPower);
        
        lastEnemyHeading = event.getHeadingRadians();
        lastScanTime = getTime();

        // execute() is called in the run loop
    }
}