package com.makd.afinity.ui.admin.config

import com.makd.afinity.R

internal val NETWORKING_FORM =
    ConfigFormSpec(
        id = ConfigFormIds.NETWORKING,
        title = R.string.config_networking_title,
        note = R.string.config_networking_note,
        sections =
            listOf(
                ConfigSection(
                    R.string.config_section_net_addresses,
                    listOf(
                        ReadOnlyField("BaseUrl", R.string.config_net_base_url),
                        ReadOnlyField("InternalHttpPort", R.string.config_net_http_port),
                        ReadOnlyField("InternalHttpsPort", R.string.config_net_https_port),
                        ReadOnlyField("PublicHttpPort", R.string.config_net_public_http_port),
                        ReadOnlyField("PublicHttpsPort", R.string.config_net_public_https_port),
                        ReadOnlyField("LocalNetworkAddresses", R.string.config_net_bind_addresses),
                        ReadOnlyField("LocalNetworkSubnets", R.string.config_net_lan_subnets),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_net_https,
                    listOf(
                        ReadOnlyField("EnableHttps", R.string.config_net_enable_https),
                        ReadOnlyField("RequireHttps", R.string.config_net_require_https),
                        ReadOnlyField("CertificatePath", R.string.config_net_certificate_path),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_net_remote,
                    listOf(
                        ReadOnlyField("EnableRemoteAccess", R.string.config_net_remote_access),
                        ReadOnlyField("RemoteIPFilter", R.string.config_net_ip_filter),
                        ReadOnlyField(
                            "IsRemoteIPFilterBlacklist",
                            R.string.config_net_ip_filter_blacklist,
                        ),
                        ReadOnlyField("EnableUPnP", R.string.config_net_upnp),
                        ReadOnlyField("KnownProxies", R.string.config_net_known_proxies),
                        ReadOnlyField(
                            "PublishedServerUriBySubnet",
                            R.string.config_net_published_uris,
                        ),
                        ReadOnlyField(
                            "EnablePublishedServerUriByRequest",
                            R.string.config_net_published_by_request,
                        ),
                    ),
                ),
                ConfigSection(
                    R.string.config_section_net_interfaces,
                    listOf(
                        ReadOnlyField("EnableIPv4", R.string.config_net_ipv4),
                        ReadOnlyField("EnableIPv6", R.string.config_net_ipv6),
                        ReadOnlyField("AutoDiscovery", R.string.config_net_auto_discovery),
                        ReadOnlyField(
                            "IgnoreVirtualInterfaces",
                            R.string.config_net_ignore_virtual,
                        ),
                        ReadOnlyField("VirtualInterfaceNames", R.string.config_net_virtual_names),
                    ),
                ),
            ),
    )
