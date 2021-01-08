package org.briarproject.briar.android.privategroup.conversation;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.briarproject.bramble.api.contact.ContactId;
import org.briarproject.bramble.api.identity.AuthorId;
import org.briarproject.bramble.api.nullsafety.MethodsNotNullByDefault;
import org.briarproject.bramble.api.nullsafety.ParametersNotNullByDefault;
import org.briarproject.briar.R;
import org.briarproject.briar.android.activity.ActivityComponent;
import org.briarproject.briar.android.privategroup.conversation.GroupController.GroupListener;
import org.briarproject.briar.android.privategroup.creation.GroupInviteActivity;
import org.briarproject.briar.android.privategroup.memberlist.GroupMemberListActivity;
import org.briarproject.briar.android.privategroup.reveal.RevealContactsActivity;
import org.briarproject.briar.android.threaded.ThreadListActivity;
import org.briarproject.briar.android.threaded.ThreadListController;
import org.briarproject.briar.android.threaded.ThreadListViewModel;
import org.briarproject.briar.api.privategroup.Visibility;

import javax.annotation.Nullable;
import javax.inject.Inject;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static org.briarproject.briar.android.activity.RequestCodes.REQUEST_GROUP_INVITE;
import static org.briarproject.briar.android.util.UiUtils.observeOnce;
import static org.briarproject.briar.api.privategroup.PrivateGroupConstants.MAX_GROUP_POST_TEXT_LENGTH;

@MethodsNotNullByDefault
@ParametersNotNullByDefault
public class GroupActivity extends
		ThreadListActivity<GroupMessageItem, GroupMessageAdapter>
		implements GroupListener {

	@Inject
	ViewModelProvider.Factory viewModelFactory;
	@Inject
	GroupController controller;

	private GroupViewModel viewModel;

	private MenuItem revealMenuItem, inviteMenuItem, leaveMenuItem,
			dissolveMenuItem;

	@Override
	public void injectActivity(ActivityComponent component) {
		component.inject(this);
		viewModel = new ViewModelProvider(this, viewModelFactory)
				.get(GroupViewModel.class);
	}

	@Override
	protected ThreadListController<GroupMessageItem> getController() {
		return controller;
	}

	@Override
	protected ThreadListViewModel<GroupMessageItem> getViewModel() {
		return viewModel;
	}

	@Override
	protected GroupMessageAdapter createAdapter() {
		return new GroupMessageAdapter(this);
	}

	@Override
	public void onCreate(@Nullable Bundle state) {
		super.onCreate(state);

		Toolbar toolbar = setUpCustomToolbar(false);

		Intent i = getIntent();
		String groupName = i.getStringExtra(GROUP_NAME);
		if (groupName != null) setTitle(groupName);
		observeOnce(viewModel.getPrivateGroup(), this, privateGroup ->
				setTitle(privateGroup.getName())
		);
		observeOnce(viewModel.isCreator(), this, adapter::setIsCreator);

		// Open member list on Toolbar click
		if (toolbar != null) {
			toolbar.setOnClickListener(v -> {
				Intent i1 = new Intent(GroupActivity.this,
						GroupMemberListActivity.class);
				i1.putExtra(GROUP_ID, groupId.getBytes());
				startActivity(i1);
			});
		}

		// start with group disabled and enable when not dissolved
		setGroupEnabled(false);
		viewModel.isDissolved().observe(this, dissolved -> {
			setGroupEnabled(!dissolved);
			if (dissolved) onGroupDissolved();
		});
	}

	@Override
	public void onStart() {
		super.onStart();
		viewModel.clearGroupMessageNotifications();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu items for use in the action bar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.group_actions, menu);

		revealMenuItem = menu.findItem(R.id.action_group_reveal);
		inviteMenuItem = menu.findItem(R.id.action_group_invite);
		leaveMenuItem = menu.findItem(R.id.action_group_leave);
		dissolveMenuItem = menu.findItem(R.id.action_group_dissolve);

		// all role-dependent items are invisible until we know our role
		revealMenuItem.setVisible(false);
		inviteMenuItem.setVisible(false);
		leaveMenuItem.setVisible(false);
		dissolveMenuItem.setVisible(false);

		// show items based on role (which will not change, so observe once)
		observeOnce(viewModel.isCreator(), this, isCreator -> {
			revealMenuItem.setVisible(!isCreator);
			inviteMenuItem.setVisible(isCreator);
			leaveMenuItem.setVisible(!isCreator);
			dissolveMenuItem.setVisible(isCreator);
		});

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int itemId = item.getItemId();
		if (itemId == R.id.action_group_member_list) {
			Intent i1 = new Intent(this, GroupMemberListActivity.class);
			i1.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i1);
			return true;
		} else if (itemId == R.id.action_group_reveal) {
			if (viewModel.isCreator().getValue())
				throw new IllegalStateException();
			Intent i2 = new Intent(this, RevealContactsActivity.class);
			i2.putExtra(GROUP_ID, groupId.getBytes());
			startActivity(i2);
			return true;
		} else if (itemId == R.id.action_group_invite) {
			if (!viewModel.isCreator().getValue())
				throw new IllegalStateException();
			Intent i3 = new Intent(this, GroupInviteActivity.class);
			i3.putExtra(GROUP_ID, groupId.getBytes());
			startActivityForResult(i3, REQUEST_GROUP_INVITE);
			return true;
		} else if (itemId == R.id.action_group_leave) {
			if (viewModel.isCreator().getValue())
				throw new IllegalStateException();
			showLeaveGroupDialog();
			return true;
		} else if (itemId == R.id.action_group_dissolve) {
			if (!viewModel.isCreator().getValue())
				throw new IllegalStateException();
			showDissolveGroupDialog();

			return super.onOptionsItemSelected(item);
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onActivityResult(int request, int result,
			@Nullable Intent data) {
		if (request == REQUEST_GROUP_INVITE && result == RESULT_OK) {
			displaySnackbar(R.string.groups_invitation_sent);
		} else super.onActivityResult(request, result, data);
	}

	@Override
	protected int getMaxTextLength() {
		return MAX_GROUP_POST_TEXT_LENGTH;
	}

	@Override
	public void onReplyClick(GroupMessageItem item) {
		if (!viewModel.isDissolved().getValue()) super.onReplyClick(item);
	}

	private void setGroupEnabled(boolean enabled) {
		sendController.setReady(enabled);
		list.getRecyclerView().setAlpha(enabled ? 1f : 0.5f);

		if (!enabled) {
			textInput.setVisibility(GONE);
			if (textInput.isKeyboardOpen()) textInput.hideSoftKeyboard();
		} else {
			textInput.setVisibility(VISIBLE);
		}
	}

	private void showLeaveGroupDialog() {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_leave_dialog_title));
		builder.setMessage(getString(R.string.groups_leave_dialog_message));
		builder.setNegativeButton(R.string.dialog_button_leave,
				(d, w) -> deleteGroup());
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void showDissolveGroupDialog() {
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_dissolve_dialog_title));
		builder.setMessage(getString(R.string.groups_dissolve_dialog_message));
		builder.setNegativeButton(R.string.groups_dissolve_button,
				(d, w) -> deleteGroup());
		builder.setPositiveButton(R.string.cancel, null);
		builder.show();
	}

	private void deleteGroup() {
		// The activity is going to be destroyed by the
		// GroupRemovedEvent being fired
		viewModel.deletePrivateGroup();
	}

	@Override
	public void onContactRelationshipRevealed(AuthorId memberId, ContactId c,
			Visibility v) {
		adapter.updateVisibility(memberId, v);

		sharingController.add(c);
		setToolbarSubTitle(sharingController.getTotalCount(),
				sharingController.getOnlineCount());
	}

	public void onGroupDissolved() {
		setGroupEnabled(false);
		AlertDialog.Builder builder =
				new AlertDialog.Builder(this, R.style.BriarDialogTheme);
		builder.setTitle(getString(R.string.groups_dissolved_dialog_title));
		builder.setMessage(getString(R.string.groups_dissolved_dialog_message));
		builder.setNeutralButton(R.string.ok, null);
		builder.show();
	}

}
