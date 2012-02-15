package com.aviary.launcher;

import java.io.File;
import java.io.IOException;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import com.aviary.android.feather.FeatherActivity;
import com.aviary.android.feather.R;
import com.aviary.android.feather.library.filters.FilterLoaderFactory;
import com.aviary.android.feather.library.moa.MoaHD;
import com.aviary.android.feather.library.moa.MoaHD.Error;
import com.aviary.android.feather.library.providers.FeatherContentProvider;
import com.aviary.android.feather.library.providers.FeatherContentProvider.ActionsDbColumns.Action;
import com.aviary.android.feather.library.utils.ImageLoader;
import com.aviary.android.feather.library.utils.StringUtils;

public class MainActivity extends Activity {

	private static final int ACTION_REQUEST_GALLERY = 99;
	private static final int ACTION_REQUEST_FEATHER = 100;
	private static final int EXTERNAL_STORAGE_UNAVAILABLE = 1;

	public static final String LOG_TAG = "feather-launcher";
	
	/** apikey is required http://developers.aviary.com/ */
	private static final String API_KEY = "xxxxx";

	/** Folder name on the sdcard where the images will be saved **/
	private static final String FOLDER_NAME = "aviary";

	Handler mHandler = new Handler();
	Button mGalleryButton;
	Button mEditButton;
	ImageView mImage;
	View mImageContainer;
	String mOutputFilePath;
	Uri mImageUri;
	int imageWidth, imageHeight;
	private File mGalleryFolder;
	
	/** session id for the hi-res post processing */
	private String mSessionId;

	@Override
	protected void onCreate( Bundle savedInstanceState ) {
		Log.i( LOG_TAG, "onCreate" );
		super.onCreate( savedInstanceState );
		setContentView( R.layout.main );

		DisplayMetrics metrics = getResources().getDisplayMetrics();
		imageWidth = (int) ( (float) metrics.widthPixels / 1.5 );
		imageHeight = (int) ( (float) metrics.heightPixels / 1.5 );

		mGalleryButton.setOnClickListener( new View.OnClickListener() {

			@Override
			public void onClick( View v ) {
				pickFromGallery();
			}
		} );

		mEditButton.setOnClickListener( new View.OnClickListener() {

			@Override
			public void onClick( View v ) {
				if ( mImageUri != null ) {
					startFeather( mImageUri );
				}
			}
		} );

		mImageContainer.setOnClickListener( new View.OnClickListener() {

			@Override
			public void onClick( View v ) {
				Uri uri = pickRandomImage();
				if ( uri != null ) {
					Log.d( LOG_TAG, "image uri: " + uri );
					setImageURI( uri );
				}
			}
		} );

		Toast.makeText( this, "launcher: " + getLibraryVersion() + ", sdk: " + FeatherActivity.SDK_VERSION, Toast.LENGTH_SHORT )
				.show();

		mGalleryFolder = createFolders();
	}
	
	@Override
	protected void onResume() {
		Log.i( LOG_TAG, "onResume" );
		super.onResume();

		if ( getIntent() != null ) {
			handleIntent( getIntent() );
			setIntent( new Intent() );
		}
	}

	/**
	 * Handle the incoming {@link Intent}
	 */
	private void handleIntent( Intent intent ) {

		String action = intent.getAction();

		if ( null != action ) {

			if ( Intent.ACTION_SEND.equals( action ) ) {

				Bundle extras = intent.getExtras();
				if ( extras != null && extras.containsKey( Intent.EXTRA_STREAM ) ) {
					Uri uri = (Uri) extras.get( Intent.EXTRA_STREAM );
					loadAsync( uri );
				}
			} else if ( Intent.ACTION_VIEW.equals( action ) ) {
				Uri data = intent.getData();
				Log.d( LOG_TAG, "data: " + data );
				loadAsync( data );
			}
		}
	}

	/**
	 * Load the incoming Image
	 * @param uri
	 */
	private void loadAsync( final Uri uri ) {

		final Runnable r = new Runnable() {

			@Override
			public void run() {
				int w = mImageContainer.getWidth();
				Log.d( LOG_TAG, "width: " + w );
				if ( w > 0 ) {
					mHandler.post( new Runnable() {

						@Override
						public void run() {
							setImageURI( uri );
						}
					} );
				} else {
					loadAsync( uri );
				}
			}
		};

		mHandler.post( r );

	}

	@Override
	protected void onDestroy() {
		Log.i( LOG_TAG, "onDestroy" );
		super.onDestroy();
		mOutputFilePath = null;
	}

	/**
	 * Delete a file without throwing any exception
	 * @param path
	 * @return
	 */
	private boolean deleteFileNoThrow( String path ) {
		File file;
		try {
			file = new File( path );
		} catch ( NullPointerException e ) {
			return false;
		}

		if ( file.exists() ) {
			return file.delete();
		}
		return false;
	}

	@Override
	public void onContentChanged() {
		super.onContentChanged();

		mGalleryButton = (Button) findViewById( R.id.button1 );
		mEditButton = (Button) findViewById( R.id.button2 );
		mImage = ( (ImageView) findViewById( R.id.image ) );
		mImageContainer = findViewById( R.id.image_container );
	}

	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate( R.menu.main_menu, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {

		Intent intent;

		final int id = item.getItemId();

		if ( id == R.id.view_documentation ) {
			intent = new Intent( Intent.ACTION_VIEW );
			intent.setData( Uri.parse( "http://www.aviary.com/android-documentation" ) );
			startActivity( intent );
		} else if ( id == R.id.get_sdk ) {

			intent = new Intent( Intent.ACTION_VIEW );
			intent.setData( Uri.parse( "https://github.com/AviaryInc/Mobile-Feather-SDK-for-Android" ) );
			startActivity( intent );
		}
		return super.onOptionsItemSelected( item );
	}

	@Override
	protected Dialog onCreateDialog( int id ) {
		Dialog dialog = null;
		switch ( id ) {
			// external sdcard is not mounted!
			case EXTERNAL_STORAGE_UNAVAILABLE:
				dialog = new AlertDialog.Builder( this ).setTitle( R.string.external_storage_na_title )
						.setMessage( R.string.external_storage_na_message ).create();
				break;
		}
		return dialog;
	}

	@Override
	/**
	 * This method is called when feather has completed ( ie. user clicked on "done" or just exit the activity without saving ). <br />
	 * If user clicked the "done" button you'll receive RESULT_OK as resultCode, RESULT_CANCELED otherwise.
	 * 
	 * @param requestCode
	 * 	- it is the code passed with startActivityForResult
	 * @param resultCode
	 * 	- result code of the activity launched ( it can be RESULT_OK or RESULT_CANCELED )
	 * @param data
	 * 	- the result data
	 */
	public void onActivityResult( int requestCode, int resultCode, Intent data ) {
		if ( resultCode == RESULT_OK ) {
			switch ( requestCode ) {
				case ACTION_REQUEST_GALLERY:
					// user chose an image from the gallery
					setImageURI( data.getData() );
					break;

				case ACTION_REQUEST_FEATHER:

					// send a notification to the media scanner
					updateMedia( mOutputFilePath );

					// update the preview with the result
					if ( setImageURI( data.getData() ) ) {
						onSaveCompleted( mOutputFilePath );
						mOutputFilePath = null;
					}
					break;
			}
		} else if ( resultCode == RESULT_CANCELED ) {
			switch ( requestCode ) {
				case ACTION_REQUEST_FEATHER:

					// feather was cancelled without saving.
					// we need to delete the entire session
					deleteSession( mSessionId );

					// delete the result file, if exists
					if ( mOutputFilePath != null ) {
						deleteFileNoThrow( mOutputFilePath );
						mOutputFilePath = null;
					}
					break;
			}
		}
	}

	/**
	 * lo-res process completed, ask the user if wants to process also the hi-res image
	 * 
	 * @param filepath
	 *           lo-res file name ( in case we want to delete it )
	 */
	private void onSaveCompleted( final String filepath ) {
		OnClickListener yesListener = new OnClickListener() {

			@Override
			public void onClick( DialogInterface dialog, int which ) {
				if ( null != mSessionId ) {
					processHD( mSessionId );
				}
				mSessionId = null;
			}
		};

		OnClickListener noListener = new OnClickListener() {

			@Override
			public void onClick( DialogInterface dialog, int which ) {

				if ( null != mSessionId ) {
					deleteSession( mSessionId );
				}

				if ( !isFinishing() ) {
					dialog.dismiss();
				}
				mSessionId = null;
			}
		};

		Dialog dialog = new AlertDialog.Builder( this ).setTitle( "HiRes" )
				.setMessage( "A low-resolution image was created. Do you want to save the hi-res image too?" )
				.setPositiveButton( android.R.string.yes, yesListener ).setNegativeButton( android.R.string.no, noListener )
				.setCancelable( false ).create();

		dialog.show();
	}

	/**
	 * Given an Uri load the bitmap into the current ImageView and resize it to fit the image container size
	 * 
	 * @param uri
	 */
	private boolean setImageURI( Uri uri ) {

		Drawable toRecycle = mImage.getDrawable();
		if ( toRecycle != null && toRecycle instanceof BitmapDrawable ) {
			if ( ( (BitmapDrawable) mImage.getDrawable() ).getBitmap() != null )
				( (BitmapDrawable) mImage.getDrawable() ).getBitmap().recycle();
		}

		Bitmap bitmap;
		try {
			bitmap = ImageLoader.loadFromUri( this, uri, imageWidth, imageHeight, null );
			Log.d( LOG_TAG, "image size: " + bitmap.getWidth() + "x" + bitmap.getHeight() );
			mImage.setImageBitmap( bitmap );
			mImage.setBackgroundDrawable( null );
		} catch ( IOException e ) {
			Toast.makeText( this, "Failed to load image " + uri, Toast.LENGTH_SHORT ).show();
			e.printStackTrace();
			return false;
		}

		mEditButton.setEnabled( true );
		mImageUri = uri;
		return true;
	}

	/**
	 * We need to notify the MediaScanner when a new file is created.
	 * In this way all the gallery applications will be notified too.
	 * 
	 * @param file
	 */
	private void updateMedia( String filepath ) {
		Log.i( LOG_TAG, "updateMedia: " + filepath );
		MediaScannerConnection.scanFile( getApplicationContext(), new String[] { filepath }, null, null );
	}

	/**
	 * Pick a random image from the user gallery
	 * 
	 * @return
	 */
	private Uri pickRandomImage() {
		Cursor c = getContentResolver().query( Images.Media.EXTERNAL_CONTENT_URI, new String[] { ImageColumns.DATA },
				ImageColumns.SIZE + ">?", new String[] { "90000" }, null );
		Uri uri = null;

		if ( c != null ) {
			int total = c.getCount();
			int position = (int) ( Math.random() * total );
			Log.d( LOG_TAG, "pickRandomImage. total images: " + total + ", position: " + position );
			if ( total > 0 ) {
				if ( c.moveToPosition( position ) ) {
					String data = c.getString( c.getColumnIndex( Images.ImageColumns.DATA ) );
					uri = Uri.parse( data );
					Log.d( LOG_TAG, uri.toString() );
				}
			}
			c.close();
		}
		return uri;
	}

	/**
	 * Return the current application version string
	 * 
	 * @return
	 */
	private String getLibraryVersion() {
		String result = "";

		try {
			PackageManager manager = getPackageManager();
			PackageInfo info = manager.getPackageInfo( getPackageName(), 0 );
			result = info.versionName;
		} catch ( Exception e ) {}

		return result;
	}

	/**
	 * Return a new image file. Name is based on the current time. Parent folder will be the one created with createFolders
	 * 
	 * @return
	 * @see #createFolders()
	 */
	private File getNextFileName() {
		if ( mGalleryFolder != null ) {
			if ( mGalleryFolder.exists() ) {
				File file = new File( mGalleryFolder, "aviary_" + System.currentTimeMillis() + ".jpg" );
				return file;
			}
		}
		return null;
	}

	/**
	 * Once you've chosen an image you can start the feather activity
	 * 
	 * @param uri
	 */
	private void startFeather( Uri uri ) {

		Log.d( LOG_TAG, "uri: " + uri );

		// first check the external storage availability
		if ( !isExternalStorageAvilable() ) {
			showDialog( EXTERNAL_STORAGE_UNAVAILABLE );
			return;
		}

		// create a temporary file where to store the resulting image
		File file = getNextFileName();
		if ( null != file ) {
			mOutputFilePath = file.getAbsolutePath();
		} else {
			new AlertDialog.Builder( this ).setTitle( android.R.string.dialog_alert_title ).setMessage( "Failed to create a new File" )
					.show();
			return;
		}

		// Create the intent needed to start feather
		Intent newIntent = new Intent( this, FeatherActivity.class );

		// set the source image uri
		newIntent.setData( uri );

		// pass the required api_key and secret ( see
		// http://developers.aviary.com/ )
		newIntent.putExtra( "API_KEY", API_KEY );

		// pass the uri of the destination image file (optional)
		// This will be the same uri you will receive in the onActivityResult
		newIntent.putExtra( "output", Uri.parse( "file://" + mOutputFilePath ) );

		// format of the destination image (optional)
		newIntent.putExtra( "output-format", Bitmap.CompressFormat.JPEG.name() );

		// output format quality (optional)
		newIntent.putExtra( "output-quality", 90 );

		// If you want to disable the external effects
		//newIntent.putExtra( "effect-enable-external-pack", false );
		
		// If you want to disable the external effects
		//newIntent.putExtra( "stickers-enable-external-pack", false );		

		// enable fast rendering preview
		// newIntent.putExtra( "effect-enable-fast-preview", true );

		// you can force feather to display only a certain ( see FilterLoaderFactory#Filters )
		// you can omit this if you just wanto to display the default tools
		newIntent.putExtra( "tools-list",
				new String[] {
					FilterLoaderFactory.Filters.ENHANCE.name(), FilterLoaderFactory.Filters.EFFECTS.name(),
					FilterLoaderFactory.Filters.STICKERS.name(), FilterLoaderFactory.Filters.ADJUST.name(),
					FilterLoaderFactory.Filters.CROP.name(), FilterLoaderFactory.Filters.BRIGHTNESS.name(),
					FilterLoaderFactory.Filters.CONTRAST.name(), FilterLoaderFactory.Filters.SATURATION.name(),
					FilterLoaderFactory.Filters.SHARPNESS.name(), FilterLoaderFactory.Filters.DRAWING.name(),
					FilterLoaderFactory.Filters.TEXT.name(), FilterLoaderFactory.Filters.MEME.name(),
					FilterLoaderFactory.Filters.RED_EYE.name(), FilterLoaderFactory.Filters.WHITEN.name(),
					FilterLoaderFactory.Filters.BLEMISH.name(), } );

		// you want the result bitmap inline. (optional)
		// newIntent.putExtra( Constants.EXTRA_RETURN_DATA, true );

		// you want to hide the exit alert dialog shown when back is pressed
		// without saving image first
		// newIntent.putExtra( Constants.EXTRA_HIDE_EXIT_UNSAVE_CONFIRMATION, true );

		final DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics( metrics );
		final int max_size = Math.max( metrics.widthPixels, metrics.heightPixels );

		// you can pass the maximum allowed image size, otherwise feather will determine
		// the max size based on the device memory
		// Here we're passing the current display size as max image size because after
		// the execution of Aviary we're saving the HI-RES image so we don't need a big
		// image for the preview
		newIntent.putExtra( "max-image-size", max_size );

		// You need to generate a new session id key to pass to Aviary feather
		// this is the key used to operate with the hi-res image ( and must be unique for every new instance of Feather )
		// The session-id key must be 64 char length
		mSessionId = StringUtils.getSha256( System.currentTimeMillis() + API_KEY );
		Log.d( LOG_TAG, "session: " + mSessionId + ", size: " + mSessionId.length() );
		newIntent.putExtra( "output-hires-session-id", mSessionId );

		// ..and start feather
		startActivityForResult( newIntent, ACTION_REQUEST_FEATHER );
	}

	/**
	 * Check the external storage status
	 * 
	 * @return
	 */
	private boolean isExternalStorageAvilable() {
		String state = Environment.getExternalStorageState();
		if ( Environment.MEDIA_MOUNTED.equals( state ) ) {
			return true;
		}
		return false;
	}

	/**
	 * Start the activity to pick an image from the user gallery
	 */
	private void pickFromGallery() {
		Intent intent = new Intent( Intent.ACTION_GET_CONTENT );
		intent.setType( "image/*" );

		Intent chooser = Intent.createChooser( intent, "Choose a Picture" );
		startActivityForResult( chooser, ACTION_REQUEST_GALLERY );
	}

	/**
	 * Try to create the required folder on the sdcard where images
	 * will be saved to.
	 * @return
	 */
	private File createFolders() {

		File baseDir;

		if ( android.os.Build.VERSION.SDK_INT < 8 ) {
			baseDir = Environment.getExternalStorageDirectory();
		} else {
			baseDir = Environment.getExternalStoragePublicDirectory( Environment.DIRECTORY_PICTURES );
		}

		if ( baseDir == null ) return Environment.getExternalStorageDirectory();

		Log.d( LOG_TAG, "Pictures folder: " + baseDir.getAbsolutePath() );
		File aviaryFolder = new File( baseDir, FOLDER_NAME );

		if ( aviaryFolder.exists() ) return aviaryFolder;
		if ( aviaryFolder.mkdirs() ) return aviaryFolder;

		return Environment.getExternalStorageDirectory();
	}

	/**
	 * Start the hi-res image processing.
	 * 
	 */
	private void processHD( final String session_name ) {
		
		// get a new file for the hi-res file
		File destination = getNextFileName();

		try {
			if ( destination == null || !destination.createNewFile() ) {
				Log.e( LOG_TAG, "Failed to create a new file" );
				return;
			}
		} catch ( IOException e ) {
			Log.e( LOG_TAG, e.getMessage() );
			Toast.makeText( this, e.getLocalizedMessage(), Toast.LENGTH_SHORT ).show();
			return;
		}
		
		String error = null;

		// Now we need to fetch the session information from the content provider
		FeatherContentProvider.SessionsDbColumns.Session session = null;
		
		Uri sessionUri = FeatherContentProvider.SessionsDbColumns.getContentUri( session_name );
		
		// this query will return a cursor with the informations about the given session
		Cursor cursor = getContentResolver().query( sessionUri, null, null, null, null );
		
		if ( null != cursor ) {
			session = FeatherContentProvider.SessionsDbColumns.Session.Create( cursor );
			cursor.close();
		}

		if ( null != session ) {
			// Print out the session informations
			Log.d( LOG_TAG, "session.id: " + session.id );	// session _id
			Log.d( LOG_TAG, "session.name: " + session.session );	// session name
			Log.d( LOG_TAG, "session.ctime: " + session.ctime );	// creation time
			Log.d( LOG_TAG, "session.file_name: " + session.file_name );	// original file, it is the same you passed in the startActivityForResult Intent

			// Now, based on the session information we need to retrieve
			// the list of actions to apply to the hi-res image
			Uri actionsUri = FeatherContentProvider.ActionsDbColumns.getContentUri( session.session );
			
			// this query will return the list of actions performed on the original file, during the FeatherActivity session.
			// Now you can apply each action to the hi-res image to replicate the same result on the bigger image
			cursor = getContentResolver().query( actionsUri, null, null, null, null );
			
			if ( null != cursor ) {
				// resolve the absolute file path of the input image
				String originalPath = ImageLoader.getRealFilePath( this, Uri.parse( session.file_name ) );
				if( originalPath != null ){
					// If the cursor is valid we will start a new asynctask process to query the cursor
					// and apply all the actions in a queue
					HDAsyncTask task = new HDAsyncTask( originalPath, destination.getAbsolutePath() );
					task.execute( cursor );
				} else {
					error = "Cannot find the file path for " + session.file_name;
				}
			} else {
				error = "Failed to retrieve the list of actions!";
			}
		} else {
			error = "Failed to retrieve the session informations";
		}
		
		if( null != error ){
			Toast.makeText( this, error, Toast.LENGTH_LONG ).show();
		}
	}

	/**
	 * Delete the session and all it's actions. We do not need it anymore.<br />
	 * Note that this is optional. All old sessions are automatically removed in Feather.
	 * 
	 * @param session_id
	 */
	private void deleteSession( final String session_id ) {
		Uri uri = FeatherContentProvider.SessionsDbColumns.getContentUri( session_id );
		getContentResolver().delete( uri, null, null );
	}

	/**
	 * AsyncTask for Hi-Res image processing
	 * @author alessandro
	 *
	 */
	private class HDAsyncTask extends AsyncTask<Cursor, Integer, MoaHD.Error> {

		String srcPath, dstPath;
		ProgressDialog progress;

		/**
		 * 
		 * @param source 
		 * 	- source image file
		 * @param destination 
		 * 	- destination image file
		 */
		public HDAsyncTask( String source, String destination ) {
			srcPath = source;
			dstPath = destination;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			progress = new ProgressDialog( MainActivity.this );
			progress.setIndeterminate( true );
			progress.setTitle( "Processing Hi-res image" );
			progress.setProgressStyle( ProgressDialog.STYLE_SPINNER );
			progress.setCancelable( false );
			progress.show();
		}

		@Override
		protected void onProgressUpdate( Integer... values ) {
			super.onProgressUpdate( values );
			//progress.setMax( values[1] );
			//progress.setProgress( values[0] );
			
			final int index = values[0];
			final int total = values[1];
			String message = "";
			
			if( index == 1 )
				message = "Loading image...";
			else if( index == total )
				message = "Saving image...";
			else
				message = "Applying action " + (index-1) + " of " + (total-2);
			
			progress.setMessage( message );
			
			Log.d( LOG_TAG, index + "/" + total + ", message: " + message );
		}

		@Override
		protected Error doInBackground( Cursor... params ) {
			Cursor cursor = params[0];
			
			MoaHD.Error result = Error.UnknownError;

			if ( null != cursor ) {
				if ( cursor.moveToFirst() ) {
					
					// Initialize the class to perform HD operations
					MoaHD moa = new MoaHD( srcPath, dstPath );
					
					// get the total number of actions in the queue
					// we're adding also the 'load' and the 'save' action to the total count
					int total_actions = cursor.getCount() + 2;
					int current_action = 1;
					
					// try to load the source image
					// If load completes succesfully it will return MoaHD.Error.NoError
					publishProgress( current_action++, total_actions );
					result = moa.load();
					
					Log.d( LOG_TAG, "moa.load: " + result.name() );

					if ( result == Error.NoError ) {
						// now for each action in the given cursor, apply the action to
						// the MoaHD instance
						do {
							// send a progress notification to the progressbar dialog
							publishProgress( current_action++, total_actions );
							
							// load the action from the current cursor
							Action action = Action.Create( cursor );
							if( null != action )
							{
								Log.d( LOG_TAG, "executing: " + action.id + "(" + action.session_id + " on " + action.ctime + ") = " + action.getActions() );
								
								// apply a list of actions to the current image
								moa.applyActions( action.getActions() );
							} else {
								Log.e( LOG_TAG, "Woa, something went wrong! Invalid action returned" );
							}

							// move the cursor to next position
						} while ( cursor.moveToNext() );

						// at the end of all the operations we need to save 
						// the modified image to a new file
						publishProgress( current_action++, total_actions );
						result = moa.save();
						
						Log.d( LOG_TAG, "moa.save: " + result.name() );

						if ( result != Error.NoError ) {
							Log.e( LOG_TAG, "failed to save the image to " + moa.getOutputFilePath() );
						}

						// and unload the current bitmap. Note that you *MUST* call this method to free the memory allocated with the load method
						result = moa.unload();
						Log.d( LOG_TAG, "moa.unload: " + result.name() );
						
						// finally dispose the moahd instance
						moa.dispose();

					} else {
						Log.e( LOG_TAG, "Failed to load file: " + moa.getInputFilePath() );
					}
				}
				cursor.close();
			}
			return result;
		}

		@Override
		protected void onPostExecute( MoaHD.Error result ) {
			super.onPostExecute( result );

			if ( progress.isShowing() ) {
				progress.dismiss();
			}

			// in case we had an error...
			if ( result != Error.NoError ) {
				Toast.makeText( MainActivity.this, "There was an error: " + result.name(), Toast.LENGTH_SHORT ).show();
				return;
			}

			// finally notify the MediaScanner of the new generated file
			updateMedia( dstPath );

			// now ask the user if he want to see the saved image
			new AlertDialog.Builder( MainActivity.this ).setTitle( "File saved" )
					.setMessage( "File saved in " + dstPath + ". Do you want to see the HD file?" )
					.setPositiveButton( android.R.string.yes, new OnClickListener() {

						@Override
						public void onClick( DialogInterface dialog, int which ) {

							Intent intent = new Intent( Intent.ACTION_VIEW );

							String filepath = dstPath;
							if ( !filepath.startsWith( "file:" ) ) {
								filepath = "file://" + filepath;
							}
							intent.setDataAndType( Uri.parse( filepath ), "image/jpeg" );
							startActivity( intent );

						}
					} ).setNegativeButton( android.R.string.no, null ).show();

			// we don't need the session anymore, now we can delete it.
			deleteSession( mSessionId );
		}
	}
}