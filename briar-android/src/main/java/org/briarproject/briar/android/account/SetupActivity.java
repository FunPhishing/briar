package org.briarproject.briar.android.account;

import android.annotation.TargetApi;
import android.content.Intent;
import android.os.Bundle;

import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.activity.BaseActivity;
import org.briarproject.briar.android.fragment.BaseFragment.BaseFragmentListener;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.lifecycle.ViewModelProvider;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.Intent.FLAG_ACTIVITY_TASK_ON_HOME;
import static org.briarproject.briar.android.BriarApplication.ENTRY_ACTIVITY;
import static org.briarproject.briar.android.account.SetupViewModel.State.CREATED;
import static org.briarproject.briar.android.account.SetupViewModel.State.AUTHORNAME;
import static org.briarproject.briar.android.account.SetupViewModel.State.CREATEACCOUNT;
import static org.briarproject.briar.android.account.SetupViewModel.State.DOZE;
import static org.briarproject.briar.android.account.SetupViewModel.State.FAILED;
import static org.briarproject.briar.android.account.SetupViewModel.State.SETPASSWORD;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class SetupActivity extends BaseActivity
		implements BaseFragmentListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	SetupViewModel viewModel;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);
		// fade-in after splash screen instead of default animation
		overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
		setContentView(R.layout.activity_fragment_container);

		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(SetupViewModel.class);

		viewModel.state.observe(this, this::onStateChanged);
	}

	private void onStateChanged(SetupViewModel.State state) {
		if (state == AUTHORNAME) {
			showInitialFragment(AuthorNameFragment.newInstance());
		} else if (state == SETPASSWORD) {
			showPasswordFragment();
		} else if (state == DOZE) {
			showDozeFragment();
		} else if (state == CREATEACCOUNT) {
			viewModel.createAccount();
		} else if (state == CREATED || state == FAILED) {
			// TODO: Show an error if failed
			showApp();
		}
	}

	void showPasswordFragment() {
		showNextFragment(SetPasswordFragment.newInstance());
	}

	@TargetApi(23)
	void showDozeFragment() {
		showNextFragment(DozeFragment.newInstance());
	}

	void showApp() {
		Intent i = new Intent(this, ENTRY_ACTIVITY);
		i.setFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_TASK_ON_HOME |
				FLAG_ACTIVITY_CLEAR_TASK | FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(i);
		supportFinishAfterTransition();
		overridePendingTransition(R.anim.screen_new_in, R.anim.screen_old_out);
	}

	@Override
	@Deprecated
	public void runOnDbThread(Runnable runnable) {
		throw new RuntimeException("Don't use this deprecated method here.");
	}

}
