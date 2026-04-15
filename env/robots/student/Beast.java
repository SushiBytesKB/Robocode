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
        ENGAGING, // circle the enemy 
        RAMMING // collide with enemy only when we get the chance
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
                break;
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

        // FSM
        double myEnergy = getEnergy();
        double enemyEnergy = event.getEnergy();

        // we go opposite of where enemy is facing relative to us
        double enemyFrontDir = Math.sin(event.getHeadingRadians() - absBearing);
        double orbitDir = (enemyFrontDir >= 0) ? -1 : 1; 
        double moveAngle = absBearing + (Math.PI / 2) * orbitDir;
        
        // Custom function to prevent wall collisions
        // Potentil TODO: This makes us slower and less accurate with our pathing. For now, worth to not take the damage from walls
        double smoothedAngle = wallSmooth(getX(), getY(), moveAngle, orbitDir);

        // Cornered if wall smoothing is too much or if we are close to enemy
        boolean isCornered = ((Math.abs(Utils.normalRelativeAngle(smoothedAngle - moveAngle)) > 1.0) || (event.getDistance() < 120));

        // either we ram or engage. onScannedRobot assumed we finished searching
        currentState = State.ENGAGING;

        if (isCornered && (myEnergy >= enemyEnergy) && (myEnergy >= 30.0)) 
        {
            currentState = State.RAMMING;
        }

        // Movement
        if (currentState == State.RAMMING)
        {
            setTurnRightRadians(Utils.normalRelativeAngle(absBearing - getHeadingRadians()));
            setAhead(event.getDistance() + 30); // Don't overcommit
        }
        else if (currentState == State.ENGAGING) 
        {
            // Circling around enemy
            double turn = Utils.normalRelativeAngle(smoothedAngle - getHeadingRadians());

            // 
            if (Math.abs(turn) > Math.PI / 2) 
            {
                turn = Utils.normalRelativeAngle(turn + Math.PI);
                setAhead(-100);
            } 
            else 
            {
                setAhead(100);
            }

            setTurnRightRadians(turn);
        }

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
        
        // Did not check for distance in case this bot snipes like a pro
        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < 10)
        {
            setFire(bulletPower);
        }

        lastEnemyHeading = event.getHeadingRadians();
        lastScanTime = getTime();

        // execute() is called in the run loop
    }

    // Prevent wall collisions
    private double wallSmooth(double currentX, double currentY, double angle, double orientation)
    {
        double fieldW = getBattleFieldWidth();
        double fieldH = getBattleFieldHeight();

        // check if line segment intersects with margin of the battlefield
        // 160 is length of segment, 30 is margin battlefield
        while (currentX + Math.sin(angle) * 160 < 30 ||
               currentX + Math.sin(angle) * 160 > fieldW - 30 ||
               currentY + Math.cos(angle) * 160 < 30 ||
               currentY + Math.cos(angle) * 160 > fieldH - 30)
        {    
            angle += orientation * 0.1; // adjust bit by bit - inspired by gradient descent hehe
        }

        return angle;
    }
}