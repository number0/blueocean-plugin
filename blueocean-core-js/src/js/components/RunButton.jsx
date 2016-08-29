/**
 * Created by cmeyers on 8/26/16.
 */

import React, { Component, PropTypes } from 'react';
import { Icon } from 'react-material-icons-blue';

import { SseBus as sseBus } from '../';
import { ToastService as toastService } from '../';

const stopProp = (event) => {
    event.stopPropagation();
};

/**
 * Run Buttons allows a pipeline or branch to be run and also be stopped thereafter.
 */
export class RunButton extends Component {

    constructor(props) {
        super(props);

        this.item = null;
        this.subscriptionId = null;

        this.state = {
            running: false,
            stopping: false,
        };

        this._initialize(props);
    }

    componentWillReceiveProps(nextProps) {
        this._initialize(nextProps);
    }

    componentDidMount() {
        this.subscriptionId = sseBus.subscribeToJob(
            (runData, event) => this._onJobEvent(runData, event),
            (event) => this._filterJob(event)
        );
    }

    componentWillUnmount() {
        sseBus.unsubscribe(this.subscriptionId);
    }

    _initialize(props) {
        if (props.pipeline) {
            this.item = props.pipeline;
        } else if (props.branch) {
            this.item = props.branch;
        }
    }

    _onJobEvent(runData, event) {

    }

    _filterJob(event) {

    }

    _onRunClick() {


        toastService.newToast({
            text: `Queued "${name}"`,
        });
    }

    _onStopClick() {


        toastService.newToast({
            text: `Stopping "${name}" #${runId}...`,
        });
    }

    render() {
        const outerClass = this.props.className ? this.props.className : '';
        const outerClassNames = outerClass.split(' ');
        const innerButtonClass = outerClassNames.indexOf('icon-button') === -1 ? 'inverse' : '';
        const stopClass = this.state.stopping ? 'stopping' : '';

        return (
            <div className={`run-button-component ${outerClass}`} onClick={(event => stopProp(event))}>
                { !this.state.running &&
                <a className={`btn run-button ${innerButtonClass}`} title="Run" onClick={() => this._onRunClick()}>
                    <Icon size={24} icon="play_circle_outline" />
                    <span className="button-label">Run</span>
                </a>
                }

                { this.state.running &&
                <a className={`stop-button ${stopClass}`} title="Stop" onClick={() => this._onStopClick()}></a>
                }
            </div>
        );
    }
}

RunButton.propTypes = {
    className: PropTypes.string,
    pipeline: PropTypes.object,
    branch: PropTypes.object,
};
