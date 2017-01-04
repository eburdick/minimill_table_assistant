package eburdick.android.com.minimill_table_assistant;

/*
Basic milling machines with no digital readouts for table position can be a challenge to
correctly position.  Many small machines known as Mini Mills make it worse because the pitch
of the lead screws is not a nice decimal fraction of an inch, but 1/16" = .0625". Calculating
a single move in X or Y requires significant arithmetic, and calculating a sequence of moves
can be fraught with error. Doing it in Millimeters compounds this. This app is designed
to solve this problem by converting a sequence coordinates to a set of simple instructions to
the operator.

User interface: The V2 user interface is based on a HTML/Javascript app I wrote to prototype
the concept. That app is a single scrolling page with a text entry field for each X and Y
coordinate layed out as follows...

This tool helps you move the mill
table to a sequence of coordinates
you specify.

Start by moving the table to your
starting position, making sure to
end each operation with clockwise
(right) rotation of the X and Y
controls.

Set both dial indicators to 0.000

Select Inches or Millimeters to be
the units you want to use

O Inches    O Millimeters    <- radio group

Fill in X0 and Y0 with the coordinates
you want to assign to the starting position

Fill in X1,Y1, etc with the rest
of the coordinates you want to
visit. Instructions will appear below
each X,Y, telling you what to do to
to move the table to that position.

[Clear All]    <- Button

X0 ___________
Y0 ___________

X1____________
Y1____________
(generated instructions to user for moving from X0 to X1)
(generated instructions to user for moving from Y0 to Y1)
X2____________
Y2____________
(generated instructions to user for moving from X1 to X2)
(generated instructions to user for moving from Y1 to Y2)
     o
     o
     o
X9____________
Y9____________
(generated instructions to user for moving from X8 to X9)
(generated instructions to user for moving from Y8 to Y9)

The web page prototype has a fixed number of coordinate pairs from x0, y0 to x9, y9, which
realistically should cover the vast majority of situations in the shop, but a more elegant
way would be to start with only x0, y0, x1, y1 and add buttons at the bottom to allow addition
or removal of pairs as needed. To start, we will do the fixed number of fields.

User dial motion comments:
    Many hole patterns will require motion in both directions in one or both axes. Because of lead
    screw backlash, this can get complicated for the user if we try to finish settings in both
    directions, so for this app, final dial settings are always clockwise.  This means the
    program needs to compensate for backlash.  Roughly speaking, with the Little
    Machine Shop minimill this code is initially written for, the X backlash is about 10 mils --
    about 1/6 turn, and the Y backlash is about 20 or 30 mils, closer to half a turn. The full
    counterclockwise turns value needs to exceed the amount of backlash. We assume no machine has
    more than one turn of backlash in either axis and add a turn in negative moves to guarantee
    backlash is taken up. To make turns counting easier, for the user, each
    positioning operation starts with advancing to zero with the appropriate offset added or
    subtracted from the subsequent motion. Examples:

    Table X is at 1.600" and next position is 1.9". Starting dial position is .0375. We need to move
    .3", which is 4 turns plus .050". The motions will be:
        - Move forward to zero.  This moves us .0625-.0375=.025 to 1.625, or 25/16"
        - Move forward .3-.025=.275. This is 4 turns clockwise to zero, then set clockwise to .025

    To reverse this motion: Table X is at 1.9" and next position is 1.6".  Starting dial position is
    .025. We need to move -.3" which is 4 turns counterclockwise (-.25), -.050". The motions will be
        - Move forward to zero. This moves us .0625-.025=.0375 to 1.9375 = 31/16
        - Move bacward .3 + .0375=.3375. This is 5 turns counterclockwise to zero, then further
        counterclockwise to .0375. We get the same result by doing 6 turns counterclockwise, then
        clockwise to .0375, which takes care of half a turn of backlash. Note this only works
        because our final value is larger that our expected backlash. If our final value was small,
        say .001", then we would have to go further counterclockwise to neutralize the backlash,
        which the user may or may not notice. Alternatively, doing 7 turns counterclockwise, then a
        full turn clockwise to take up any backlash, followed by clockwise motion to the final value
        is guaranteed to compensate for all backlash of less than a turn.

    Conclusion for motion conventions:

    The first example shows that motion in a positive direction is straightforward with no
    backlash issues. The second example illustrates the backlash issue. Further thinking this out,
    if we do a number of full counterclockwise turns to zero on the dial and then do our setting in
    a clockwise direction, then unless the final setting is larger than the amount of backlash, we
    will not succeed in taking up the backlash. The simplest solution to this is to add 2 full
    turns to the counterclockwise count, then advance a full turn clockwise to take up the backlash,
    tnen move clockwise to the final setting. Alternatively, we could add either 1 or 2 extra turns
    depending on whether the final setting is enough to take up the backlash. From a human factors
    point of view, this means the user will be doing two different operations at the end of
    counterclockwise settings depending on final value, which will result in more mistakes, so in
    this app, we will add the two turns every time, which covers up to one turn of backlash.
    */

import android.net.Uri;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.Toast;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    //
    // Constants
    //
    final int NUMCOORDS = 10;
    final BigDecimal MMPERINCH = new BigDecimal("25.4");

    //
    // Declare working arrays as instance variables so they are accessible throughout
    // the class. These arrays are populated in the activity's onCreate() method.
    //
    EditText[] coordx_widget;      //X coordinate widgets
    EditText[] coordy_widget;      //Y coordinate widgets
    TextView[] user_instrx; //X user instruction widgets
    TextView[] user_instry; //y user instruction widgets
    boolean inch_units;    //if true, user coordinates are inches, else millimeters
    double dialx, dialy;     //the running dial values for the x and y controls.

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //
        // This function is called when the activity is created, so it is basically the
        // initialization code for the activity.  It sets up the user interface context,
        // and sets up action (callback) routines.
        //
        //                  Set up activity context
        //
        //The super.onCreate call restores any saved state to the activity and sets up
        //any required linkages to the OS user interface.  This is a call
        //to the parent class, which is in the operating system's user interface layer.
        //
        //The setContentView call sets up a connection to the activity's screen layout
        //resources defined in activity_main.xml (R.layout.activity_main) and "inflates"
        //it.
        //
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //
        // These are the arrays where we keep the references to the user interface
        // objects, including the EditText widgets where the user specifies the
        // coordinates and the TextView widgets where we write user instructions.
        //
        // The arrays were declared at the top level of the class to make them available
        // to all methods in the class. For each array, we allocate memory for each array
        // and then load them with the view reference identifiers.
        //
        coordx_widget = new EditText[NUMCOORDS];
        coordx_widget[0] = (EditText) findViewById(R.id.x0_input);
        coordx_widget[1] = (EditText) findViewById(R.id.x1_input);
        coordx_widget[2] = (EditText) findViewById(R.id.x2_input);
        coordx_widget[3] = (EditText) findViewById(R.id.x3_input);
        coordx_widget[4] = (EditText) findViewById(R.id.x4_input);
        coordx_widget[5] = (EditText) findViewById(R.id.x5_input);
        coordx_widget[6] = (EditText) findViewById(R.id.x6_input);
        coordx_widget[7] = (EditText) findViewById(R.id.x7_input);
        coordx_widget[8] = (EditText) findViewById(R.id.x8_input);
        coordx_widget[9] = (EditText) findViewById(R.id.x9_input);

        coordy_widget = new EditText[NUMCOORDS];
        coordy_widget[0] = (EditText) findViewById(R.id.y0_input);
        coordy_widget[1] = (EditText) findViewById(R.id.y1_input);
        coordy_widget[2] = (EditText) findViewById(R.id.y2_input);
        coordy_widget[3] = (EditText) findViewById(R.id.y3_input);
        coordy_widget[4] = (EditText) findViewById(R.id.y4_input);
        coordy_widget[5] = (EditText) findViewById(R.id.y5_input);
        coordy_widget[6] = (EditText) findViewById(R.id.y6_input);
        coordy_widget[7] = (EditText) findViewById(R.id.y7_input);
        coordy_widget[8] = (EditText) findViewById(R.id.y8_input);
        coordy_widget[9] = (EditText) findViewById(R.id.y9_input);

        user_instrx = new TextView[NUMCOORDS-1];
        user_instrx[0] = (TextView) findViewById(R.id.user_instruct_x1);
        user_instrx[1] = (TextView) findViewById(R.id.user_instruct_x2);
        user_instrx[2] = (TextView) findViewById(R.id.user_instruct_x3);
        user_instrx[3] = (TextView) findViewById(R.id.user_instruct_x4);
        user_instrx[4] = (TextView) findViewById(R.id.user_instruct_x5);
        user_instrx[5] = (TextView) findViewById(R.id.user_instruct_x6);
        user_instrx[6] = (TextView) findViewById(R.id.user_instruct_x7);
        user_instrx[7] = (TextView) findViewById(R.id.user_instruct_x8);
        user_instrx[8] = (TextView) findViewById(R.id.user_instruct_x9);

        user_instry = new TextView[NUMCOORDS-1];
        user_instry[0] = (TextView) findViewById(R.id.user_instruct_y1);
        user_instry[1] = (TextView) findViewById(R.id.user_instruct_y2);
        user_instry[2] = (TextView) findViewById(R.id.user_instruct_y3);
        user_instry[3] = (TextView) findViewById(R.id.user_instruct_y4);
        user_instry[4] = (TextView) findViewById(R.id.user_instruct_y5);
        user_instry[5] = (TextView) findViewById(R.id.user_instruct_y6);
        user_instry[6] = (TextView) findViewById(R.id.user_instruct_y7);
        user_instry[7] = (TextView) findViewById(R.id.user_instruct_y8);
        user_instry[8] = (TextView) findViewById(R.id.user_instruct_y9);

        //
        // initialize radio button state flag based on the current state
        // of the button
        //
        RadioButton inch_button = (RadioButton) findViewById(R.id.inch_sel);
        inch_units = inch_button.isChecked();

        //
        //                  Set up text widget editor action callbacks
        //
        // Set an action listener for each coordinate text widget.  These will get called when
        // the user is finished with the editor and touches the "done" or "next" button.  We
        // need this because we want our do_calculate method to be called when text entry
        // is complete on any widget, with the goal of never having stale data on the screen
        // at any time.  We could have had a "Calculate" button instead, but with the
        // scrolling screen, this would be awkward, and also result in errors when a user
        // forgot to touch it.
        //
        for (int i = 0; i < NUMCOORDS; i++) {
            coordx_widget[i].setOnEditorActionListener(new TextView.OnEditorActionListener()
            {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    //
                    // The only action we want to do when the user is finished with entering
                    // a number is to calculate the results, so we just call the do_calculate
                    // method.
                    //
                    do_calculate();
                    //
                    // Returning false tells the system that the calculate action we just did is
                    // not to consume the event.  This means that the event can still trigger
                    // removal of the soft keyboard after returning from this action method.
                    //
                    return false;
                }
            });

            coordy_widget[i].setOnEditorActionListener(new TextView.OnEditorActionListener()
            {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
                {
                    //
                    // The only action we want to do when the user is finished with entering
                    // a number is to calculate the results, so we just call the do_calculate
                    // method.
                    //
                    do_calculate();
                    //
                    // Returning false tells the system that the calculate action we just did is
                    // not to consume the event.  This means that the event can still trigger
                    // removal of the soft keyboard after returning from this action method.
                    //
                    return false;
                }
            });
        } // for loop

        //
        // Initialize the running dialx and dialy values to zero
        //
        dialx = 0.0;
        dialy = 0.0;

        //
        // Set the user interface to its empty initial state
        //
        do_clear_all();

    } // end of onCreate method

    //
    // Radio button click handler. This method is associated with the buttons
    // in activity_main.xml. If one of these radio buttons is clicked, then this
    // method will be called.  We just check which radio button was clicked
    // (Inches or Millimeters) and set the units flag appropriately.
    //
    // If we have changed something that will change the results, we also
    // call do_calculate();
    //

    public void onRadioButtonClicked(View view)
    {
        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();
        boolean old_val = inch_units;
        // Check which radio button was clicked and set or clear the inch_units
        // flag accordingly
        switch (view.getId())
        {
            case R.id.inch_sel:
                if (checked)
                    inch_units = true;
                if (old_val != inch_units)
                {
                    do_calculate();
                }
                break;
            case R.id.mm_sel:
                if (checked)
                    inch_units = false;
                if (old_val != inch_units)
                {
                    do_calculate();
                }

                break;
        }
    } // end of onRadioButtonClicked method

    public void do_clear_all()
    {
        //
        // This method clears all of the coordinate input fields and then calls
        // do_calculate() to update the user instructions.  This version takes
        // no arguements.
        //

        for (int i = 0; i < NUMCOORDS; i++)
        {
            coordx_widget[i].setText("");
            coordy_widget[i].setText("");
        }
        do_calculate();
    }

    public void do_clear_all(View view)
    {
        //
        // This method clears all of the coordinate input fields and then calls
        // do_calculate() to update the user instructions. This version takes
        // a view argument to match the call defined in the XML onClick value.
        // The view argument is not used
        //

        for (int i = 0; i < NUMCOORDS; i++)
        {
            coordx_widget[i].setText("");
            coordy_widget[i].setText("");
        }
        do_calculate();
    }

    public void do_calculate()
    {

        //
        // This method calculates all required user actions for table motion between user
        // entered coordinates based on the values of the input EditView text fields. This
        // method is called whenever the state of the user interface changes, including the
        // setting of the inches/millimeters radio group or the value of any of the coordinate
        // fields.  It is also called by do_clear_all() after it clears the coordinate fields.
        // This method sequentially calls the calcStepsAndDial() method for each pair of
        // coordinates X[n] -> X[n+1] and Y[n] -> Y[n+1].  That method calculates required
        // user operations for each motion and updates the incremental dial position.
        //
        // Declare coordinate arrays as BigDecimal.
        //
        BigDecimal[] xcoord = new BigDecimal[NUMCOORDS];
        BigDecimal[] ycoord = new BigDecimal[NUMCOORDS];
        BigDecimal scale_factor;
        BigDecimal uninitflag = new BigDecimal(1000000.0); //indicates no user entry
        String temp_coord_string;
        int index;
        //
        // Get the coordinate values from the widgets. If the coordinate value string is
        // null, then set the value to uninitflag;
        //
        for (index=0; index<NUMCOORDS; index++)
        {
            temp_coord_string = coordx_widget[index].getText().toString();
            if (temp_coord_string.equals(""))
            {
                xcoord[index] = uninitflag;
            }
            else
            {
                xcoord[index] = roundToHalfThousanth(new BigDecimal(temp_coord_string));
            }
            temp_coord_string = coordy_widget[index].getText().toString();
            if (temp_coord_string.equals(""))
            {
                ycoord[index] = uninitflag;
            }
            else
            {
                ycoord[index] = roundToHalfThousanth(new BigDecimal(temp_coord_string));
            }
        }
        //
        // Set scale factor based on the inch_units flag
        //
        if(inch_units)
        {
            scale_factor = BigDecimal.ONE;
        }
        else
        {
            scale_factor = MMPERINCH;
        }

        //
        //
        //
        //
        // For each coordinate, write the rounded value back to the input field. For adjacent
        // coordinates, eg x0, x1, call
        //    CalcStepsAndDial(starting_pos, next_pos, coord_label, scale_factor)
        // This will return the user instruction to be written to the corresponding
        // user_instrx or user_instry. As a side effect, it will also update the instance
        // variable dialx or dialy.
        //

        // Write back the rounded value of x0 and y0 to the coordinate widgets.
        if (!xcoord[0].equals(uninitflag))
        {
            coordx_widget[0].setText(String.format(Locale.US,"%1$06.4f",xcoord[0]));
        }
        if (!ycoord[0].equals(uninitflag))
        {
            coordy_widget[0].setText(String.format(Locale.US, "%1$06.4f", ycoord[0]));
        }

        //
        // note this for loop is for writing to the user_instruction arrays, which are
        // one smaller than the coordinate array.  Coordinates are indexed one higher than
        // user instructions.
        //
        for (index=0; index<NUMCOORDS-1; index++)
        {
            //
            // Write back the rounded values of the x and y coordinate to the corresponding
            // coordinate input widgets.
            //
            // temporary for test: write the coordinates to the corresponding user instruction
            // text widgets
            //
            if (xcoord[index+1].equals(uninitflag))
            {
                // temp for test: user instruction: x coordinate undefined
                user_instrx[index].setText(R.string.x_coord_undefined);
            }
            else
            {
                //write back rounded coordinate
                coordx_widget[index+1].setText(String.format(Locale.US,"%1$06.4f",xcoord[index+1]));

                //temp for test: user instruction for index,index+1 is xcoord[index+1]
                user_instrx[index].setText(String.format(Locale.US,"X coordinate is %1$06.4f",xcoord[index+1]));

            }
            if (ycoord[index+1].equals(uninitflag))
            {
                user_instry[index].setText(R.string.y_coord_undefined);
            }
            else
            {
                coordy_widget[index+1].setText(String.format(Locale.US,"%1$06.4f",ycoord[index+1]));

                user_instry[index].setText(String.format(Locale.US,"Y coordinate is %1$06.4f",ycoord[index+1]));
            }

        }

    }

    public String CalcStepsAndDial(BigDecimal starting_pos, BigDecimal next_pos, String coord_label, BigDecimal scale_factor)
    {
        //
        // check if starting_pos or next_pos is undefined. If so, nothing can be done from here on.
        //

        //
        // Calculate offset and divide by scale_factor
        //

        return ("foo, just to make compile run");


    }
    public BigDecimal roundToHalfThousanth(BigDecimal value)
    {
        //round a number to a half a thousanth. Fourth decimal place will be either 0 or 5.
        //Algorithm: multiply the number by 2000 and round it to integer, then divide by
        //2000. In BigDecimal, this is done as follows:
        //
        // multiply input value by 2000
        // round it to zero decimal places using HALF_UP rounding
        // divide result by 2000
        //   The result in the fourth decimal place will be
        //      0 if the rounding resulted in an even result
        //      5 if the rounding resulted in an odd result
        //
        // example:
        // value = 10.12324
        // times 2000 -> 20246.48
        // set scale zero with rounding -> 20246.00...
        // divide by 2000 -> 10.1230
        //
        // example:
        // value = 10.12326
        // times 2000 -> 20246.52
        // set scale zero with rounding -> 20247.00...
        // divide by 2000 -> 10.1235

        BigDecimal two_thousand = new BigDecimal(2000);
        return two_thousand.multiply(value).setScale(0, RoundingMode.HALF_UP).divide(two_thousand);

    }

} // end of public class MainActivity